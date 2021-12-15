/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.time.*
import com.ivianuu.essentials.util.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.common.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.charset.*
import java.util.*

@Provide @Scoped<AppScope> class MinirigRemote(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val broadcastsFactory: BroadcastsFactory,
  private val context: IOContext,
  private val scope: NamedCoroutineScope<AppScope>,
  private val L: Logger
) {
  private val sockets = RefCountedResource<String, MinirigSocket>(
    create = { address ->
      val device = bluetoothManager.adapter.getRemoteDevice(address)
      MinirigSocket(
        socket = device.createRfcommSocketToServiceRecord(CLIENT_ID)
          .also { socket ->
            val connectComplete = CompletableDeferred<Unit>()
            par(
              {
                log { "connect ${device.readableName()}" }
                try {
                  var attempt = 0
                  while (attempt < 5) {
                    try {
                      socket.connect()
                      if (socket.isConnected) {
                        log { "connected to ${device.readableName()}" }
                        break
                      }
                    } catch (e: Throwable) {
                      log { "connect failed ${device.readableName()} attempt $attempt" }
                      e.nonFatalOrThrow()
                      attempt++
                      delay(1000)
                    }
                  }
                } finally {
                  connectComplete.complete(Unit)
                }
              },
              {
                onCancel(
                  block = { connectComplete.await() },
                  onCancel = {
                    log { "cancel connect ${device.readableName()}" }
                    catch { socket.close() }
                  }
                )
              }
            )
          }
      )
    },
    release = { _, socket ->
      log { "release connection ${socket.device.readableName()}" }
      socket.close()
    }
  )

  fun isConnected(address: String) = bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map { address.isConnected() }
    .distinctUntilChanged()

  private fun String.isConnected(): Boolean =
    bluetoothManager.adapter.getRemoteDevice(this)
      ?.let {
        BluetoothDevice::class.java.getDeclaredMethod("isConnected").invoke(it)
          .cast<Boolean>()
      } ?: false

  suspend fun <R> withMinirig(
    address: String,
    block: suspend MinirigSocket.() -> R
  ): R? = withContext(context) {
    if (!address.isConnected()) null
    else guarantee(
      block = {
        val socket = sockets.acquire(address)
        block(socket)
      },
      finalizer = {
        sockets.release(address)
      }
    )
  }

  fun bondedDeviceChanges() = broadcastsFactory(
    BluetoothAdapter.ACTION_STATE_CHANGED,
    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
    BluetoothDevice.ACTION_ACL_CONNECTED,
    BluetoothDevice.ACTION_ACL_DISCONNECTED
  )
}

class MinirigSocket(
  private val socket: BluetoothSocket,
  @Inject context: IOContext,
  scope: NamedCoroutineScope<AppScope>,
  L: Logger
) {
  private val scope = scope.childCoroutineScope(context)

  val device get() = socket.remoteDevice

  val messages = channelFlow {
    while (currentCoroutineContext().isActive && socket.isConnected) {
      if (socket.inputStream.available() > 0) {
        val arr = ByteArray(socket.inputStream.available())
        socket.inputStream.read(arr)
        val current = arr.toString(StandardCharsets.UTF_8)
        log { "${socket.remoteDevice.readableName()} stats $current" }
        send(current)
      }
    }
  }.shareIn(scope, SharingStarted.Eagerly)

  private val sendLimiter = RateLimiter(1, 100.milliseconds)

  suspend fun send(message: String) {
    // the minirig cannot keep with our speed to debounce each write
    sendLimiter.acquire()
    socket.outputStream.write(message.toByteArray())
  }

  fun close() {
    scope.cancel()
    catch {
      socket.close()
    }
  }
}

val CLIENT_ID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")!!
