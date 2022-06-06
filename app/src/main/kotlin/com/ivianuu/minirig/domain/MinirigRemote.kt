/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.catch
import com.ivianuu.essentials.coroutines.RateLimiter
import com.ivianuu.essentials.coroutines.RefCountedResource
import com.ivianuu.essentials.coroutines.childCoroutineScope
import com.ivianuu.essentials.coroutines.onCancel
import com.ivianuu.essentials.coroutines.par
import com.ivianuu.essentials.coroutines.withResource
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.asLog
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.nonFatalOrThrow
import com.ivianuu.essentials.time.milliseconds
import com.ivianuu.essentials.time.seconds
import com.ivianuu.essentials.util.BroadcastsFactory
import com.ivianuu.injekt.Inject
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import com.ivianuu.injekt.common.Scoped
import com.ivianuu.injekt.coroutines.IOContext
import com.ivianuu.injekt.coroutines.NamedCoroutineScope
import com.ivianuu.minirig.data.debugName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

@Provide @Scoped<AppScope> class MinirigRemote(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val broadcastsFactory: BroadcastsFactory,
  private val context: IOContext,
  private val scope: NamedCoroutineScope<AppScope>,
  private val logger: Logger
) {
  private val sockets = RefCountedResource<String, MinirigSocket>(
    scope = scope,
    timeout = 10.seconds,
    create = { address ->
      MinirigSocket(address)
        .also {
          log { "create socket ${it.device.debugName()}" }
        }
    },
    release = { _, socket ->
      log { "release socket $socket ${socket.device.debugName()}" }
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
        BluetoothDevice::class.java.getDeclaredMethod("isConnected").invoke(it) as Boolean
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

private val sendLimiter = RateLimiter(1, 100.milliseconds)

class MinirigSocket(
  private val address: String,
  @Inject private val bluetoothManager: @SystemService BluetoothManager,
  @Inject context: IOContext,
  @Inject parentScope: NamedCoroutineScope<AppScope>,
  @Inject private val logger: Logger
) {
  private val scope = parentScope.childCoroutineScope(context)

  var socket: BluetoothSocket? = null
  private val socketLock = Mutex()

  val device: BluetoothDevice
    get() = bluetoothManager.adapter.getRemoteDevice(address)

  val messages: Flow<String> = channelFlow {
    while (currentCoroutineContext().isActive) {
      if (!bluetoothManager.adapter.isEnabled) {
        delay(5.seconds)
        continue
      }

      try {
        withSocket("message receiver ${device.debugName()} ${this@MinirigSocket}") {
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
        delay(RetryDelay)
      }
    }
  }.shareIn(scope, SharingStarted.Eagerly)

  suspend fun send(message: String) = catch {
    suspend fun sendImpl(message: String, attempt: Int) {
      try {
        // the minirig cannot keep with our speed to debounce each write
        sendLimiter.acquire()

        withTimeout(PingPongTimeout) {
          withContext(scope.coroutineContext) {
            log { "send ${device.debugName()} -> $message attempt $attempt" }

            try {
              withSocket("send message ${device.debugName()}") {
                outputStream.write(message.toByteArray())
              }
            } catch (e: IOException) {
              closeCurrentSocket(e)
              throw e
            }
          }
        }
      } catch (e: Throwable) {
        e.nonFatalOrThrow()
        if (attempt < 5) {
          delay(RetryDelay)
          sendImpl(message, attempt + 1)
        }
      }
    }

    sendImpl(message, 0)
  }

  suspend fun close() {
    withContext(scope.coroutineContext) {
      scope.cancel()
      closeCurrentSocket(null)
    }
  }

  private suspend fun closeCurrentSocket(reason: Throwable?) {
    socketLock.withLock {
      closeCurrentSocketImpl(reason)
    }
  }

  private fun closeCurrentSocketImpl(reason: Throwable?) {
    catch {
      socket
        ?.also { log { "${device.debugName()} close current socket ${reason?.asLog()}" } }
        ?.close()
    }
    socket = null
  }

  private suspend fun withSocket(
    jobName: String,
    block: suspend BluetoothSocket.() -> Unit
  ) {
    val socket = socketLock.withLock {
      var socket = socket
      if (socket != null && socket.isConnected)
        return@withLock socket

      closeCurrentSocketImpl(null)

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
                delay(RetryDelay)
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
