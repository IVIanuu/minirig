/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import androidx.compose.ui.platform.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.time.*
import com.ivianuu.essentials.util.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.common.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import java.io.*
import java.nio.charset.*
import java.util.*
import kotlin.system.*

@Provide @Scoped<AppScope> class MinirigRemote(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val broadcastsFactory: BroadcastsFactory,
  private val context: IOContext,
  private val scope: NamedCoroutineScope<AppScope>,
  private val L: Logger
) {
  private val sockets = RefCountedResource<String, MinirigSocket>(
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
        BluetoothDevice::class.java.getDeclaredMethod("isConnected").invoke(it)
          .cast<Boolean>()
      } ?: false

  suspend fun <R> withMinirig(
    address: String,
    jobName: String,
    block: suspend MinirigSocket.() -> R
  ): R? = runJob(jobName) {
    withContext(context) {
      if (!address.isConnected()) null
      else sockets.withResource(address, block)
    }
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
  context: IOContext,
  parentScope: NamedCoroutineScope<AppScope>,
  private val L: Logger
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
    runJob("send message ${device.debugName()}") {
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
    runJob(jobName) {
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
}

val CLIENT_ID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")!!
