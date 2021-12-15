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
import kotlinx.coroutines.sync.*
import java.io.*
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
    timeout = 2.seconds,
    create = { address ->
      MinirigSocket(address)
    },
    release = { _, socket ->
      log { "release connection ${socket.device.debugName()}" }
      socket.close()
    }
  )

  fun isConnected(address: String) = bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map { address.isConnected() }
    .distinctUntilChanged()
    .flowOn(context)

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
    else sockets.withResource(address, block)
  }

  fun bondedDeviceChanges() = broadcastsFactory(
    BluetoothAdapter.ACTION_STATE_CHANGED,
    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
    BluetoothDevice.ACTION_ACL_CONNECTED,
    BluetoothDevice.ACTION_ACL_DISCONNECTED
  )
}

class MinirigSocket(
  private val address: String,
  @Inject private val bluetoothManager: @SystemService BluetoothManager,
  context: IOContext,
  scope: NamedCoroutineScope<AppScope>,
  private val L: Logger
) {
  private val scope = scope.childCoroutineScope(context)

  var socket: BluetoothSocket? = null
  private val socketLock = Mutex()

  val device: BluetoothDevice
    get() = bluetoothManager.adapter.getRemoteDevice(address)

  val messages = channelFlow<String> {
    while (currentCoroutineContext().isActive) {
      if (!bluetoothManager.adapter.isEnabled) {
        delay(5000)
        continue
      }

      try {
        withSocket {
          while (currentCoroutineContext().isActive) {
            if (inputStream.available() > 0) {
              val arr = ByteArray(inputStream.available())
              inputStream.read(arr)
              val current = arr.toString(StandardCharsets.UTF_8)
              log { "${device.debugName()} stats $current" }
              send(current)
            }
          }
        }
      } catch (e: IOException) {
        closeCurrentSocket(e)
        delay(2000)
      }
    }
  }.shareIn(scope, SharingStarted.Eagerly)

  private val sendLimiter = RateLimiter(1, 100.milliseconds)

  suspend fun send(message: String) = withContext(scope.coroutineContext) {
    // the minirig cannot keep with our speed to debounce each write
    sendLimiter.acquire()

    log { "send ${device.debugName()} -> $message" }

    try {
      withSocket {
        outputStream.write(message.toByteArray())
      }
    } catch (e: IOException) {
      closeCurrentSocket(e)
      throw e
    }
  }

  suspend fun close() = withContext(scope.coroutineContext) {
    scope.cancel()
    closeCurrentSocket(null)
  }

  private suspend fun closeCurrentSocket(reason: Throwable?) {
    withContext(NonCancellable) {
      socketLock.withLock {
        catch {
          socket
            ?.also { log { "${device.debugName()} close current socket ${reason?.asLog()}" } }
            ?.close()
        }
        socket = null
      }
    }
  }

  private suspend fun withSocket(block: suspend BluetoothSocket.() -> Unit) {
    val socket = socketLock.withLock {
      var socket = socket
      check(socket == null || socket.isConnected)
      if (socket != null)
        return@withLock socket

      socket = device.createRfcommSocketToServiceRecord(CLIENT_ID)

      val connectComplete = CompletableDeferred<Unit>()
      par(
        {
          log { "connect ${device.debugName()}" }
          try {
            var attempt = 0
            while (attempt < 5) {
              try {
                socket.connect()
                if (socket.isConnected) {
                  log { "connected to ${device.debugName()}" }
                  break
                }
              } catch (e: Throwable) {
                log { "connect failed ${device.debugName()} attempt $attempt" }
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
              log { "cancel connect ${device.debugName()}" }
              catch { socket.close() }
            }
          )
        }
      )

      this.socket = socket

      socket
    }

    block(socket)
  }
}

val CLIENT_ID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")!!
