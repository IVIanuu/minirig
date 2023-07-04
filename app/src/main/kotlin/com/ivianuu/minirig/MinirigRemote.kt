/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.Scoped
import com.ivianuu.essentials.compose.compositionStateFlow
import com.ivianuu.essentials.coroutines.CoroutineContexts
import com.ivianuu.essentials.coroutines.EventFlow
import com.ivianuu.essentials.coroutines.RefCountedResource
import com.ivianuu.essentials.coroutines.ScopedCoroutineScope
import com.ivianuu.essentials.coroutines.childCoroutineScope
import com.ivianuu.essentials.coroutines.onCancel
import com.ivianuu.essentials.coroutines.par
import com.ivianuu.essentials.coroutines.withResource
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.asLog
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.nonFatalOrThrow
import com.ivianuu.essentials.result.catch
import com.ivianuu.essentials.time.seconds
import com.ivianuu.essentials.util.BroadcastsFactory
import com.ivianuu.injekt.Inject
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.system.measureTimeMillis

@Provide @Scoped<AppScope> class MinirigRemote(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val broadcastsFactory: BroadcastsFactory,
  private val coroutineContexts: CoroutineContexts,
  private val logger: Logger,
  private val scope: ScopedCoroutineScope<AppScope>
) {
  private val sockets = RefCountedResource<String, MinirigSocket>(
    timeout = 5.seconds,
    create = { address ->
      MinirigSocket(address)
        .also {
          logger.log { "create socket ${it.device.debugName()}" }
        }
    },
    release = { _, socket ->
      logger.log { "release socket $socket ${socket.device.debugName()}" }
      socket.close()
    }
  )

  private val states = mutableMapOf<String, Flow<MinirigState>>()
  private val statesLock = Mutex()

  private val forceStateRefresh = EventFlow<String>()

  fun forceStateRefresh(address: String) {
    forceStateRefresh.tryEmit(address)
  }

  fun minirigState(address: String): Flow<MinirigState> = flow {
    emitAll(
      statesLock.withLock {
        states.getOrPut(address) {
          var initial: MinirigState? = null
          flow {
            coroutineScope {
              emitAll(minirigStateImpl(address, initial))
            }
          }
            .onEach { initial = it }
            .shareIn(scope, SharingStarted.WhileSubscribed(), 1)
        }
      }
    )
  }

  private fun minirigStateImpl(
    address: String,
    initial: MinirigState?,
    @Inject scope: CoroutineScope
  ) = scope.compositionStateFlow {
    val isConnected by remember { isConnected(address) }.collectAsState(initial?.isConnected ?: false)

    if (!isConnected)
      return@compositionStateFlow MinirigState(isConnected = false)

    LaunchedEffect(true) {
      withMinirig(address) {
        merge(
          forceStateRefresh
            .filter { it == address },
          flow<Unit> {
            // wait for the message receiver to initialize
            delay(100)

            while (true) {
              emit(Unit)
              delay(5.seconds)
            }
          },
          bondedDeviceChanges()
        )
          .collect {
            par(
              { catch { send("x") } },
              { catch { send("B") } }
            )
          }
      }
    }

    val batteryPercentage by produceState(initial?.batteryPercentage) {
      withMinirig(address) {
        messages
          .filter { it.startsWith("B") }
          .collect { message ->
            value = message
              .removePrefix("B")
              .take(5)
              .toIntOrNull()
              ?.toBatteryPercentage()
          }
      }
    }

    val powerState by produceState(initial?.powerState ?: PowerState.NORMAL) {
      withMinirig(address) {
        messages
          .filter { it.startsWith("x ") && it.length >= 9 }
          .map {
            when (it.substring(8, 9)) {
              "1" -> PowerState.NORMAL
              "2" -> PowerState.CHARGING
              "3" -> PowerState.POWER_OUT
              else -> PowerState.NORMAL
            }
          }
          .collect { value = it }
      }
    }

    val twsState by produceState(initial?.twsState ?: TwsState.NONE) {
      withMinirig(address) {
        messages
          .filter { it.startsWith("x ") && it.length >= 7 }
          .map {
            when (it.substring(5, 7)) {
              "30" -> TwsState.SLAVE
              "31" -> TwsState.MASTER
              else -> TwsState.NONE
            }
          }
          .collect { value = it }
      }
    }

    MinirigState(
      isConnected = true,
      batteryPercentage = batteryPercentage,
      powerState = powerState,
      twsState = twsState
    )
  }

  fun isConnected(address: String): Flow<Boolean> = bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map { address.isConnected() }
    .distinctUntilChanged()
    .flowOn(coroutineContexts.io)

  private fun String.isConnected(): Boolean =
    bluetoothManager.adapter.getRemoteDevice(this)
      ?.let {
        BluetoothDevice::class.java.getDeclaredMethod("isConnected").invoke(it) as Boolean
      } ?: false

  suspend fun <R> withMinirig(
    address: String,
    block: suspend MinirigSocket.() -> R
  ): R? = withContext(coroutineContexts.io) {
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

private fun Int.toBatteryPercentage(): Float = when {
  this < 10300 -> 0.01f
  this in 10300..10549 -> 0.1f
  this in 10550..10699 -> 0.2f
  this in 10700..10799 -> 0.3f
  this in 10800..10899 -> 0.4f
  this in 10900..11099 -> 0.5f
  this in 11100..11349 -> 0.6f
  this in 11350..11699 -> 0.7f
  this in 11700..11999 -> 0.8f
  this in 12000..12299 -> 0.9f
  else -> 1f
}

class MinirigSocket(
  private val address: String,
  @Inject private val bluetoothManager: @SystemService BluetoothManager,
  @Inject coroutineContexts: CoroutineContexts,
  @Inject private val logger: Logger,
  @Inject parentScope: ScopedCoroutineScope<AppScope>
) {
  private val scope = parentScope.childCoroutineScope(coroutineContexts.io)

  var socket: BluetoothSocket? = null
  private val socketLock = Mutex()

  val device: BluetoothDevice
    get() = bluetoothManager.adapter.getRemoteDevice(address)

  val messages: Flow<String> = channelFlow {
    while (currentCoroutineContext().isActive) {
      if (!bluetoothManager.adapter.isEnabled) {
        delay(1.seconds)
        continue
      }

      try {
        withSocket {
          while (currentCoroutineContext().isActive) {
            if (inputStream.available() > 0) {
              val arr = ByteArray(inputStream.available())
              inputStream.read(arr)
              val current = arr.toString(StandardCharsets.UTF_8)
              logger.log { "${device.debugName()} stats $current" }
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
    logger.log { "send ${device.debugName()} -> $message" }

    withSocket {
      outputStream.write(message.toByteArray())
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
        ?.also { logger.log { "${device.debugName()} close current socket ${reason?.asLog()}" } }
        ?.close()
    }
    socket = null
  }

  @SuppressLint("MissingPermission")
  private suspend fun withSocket(block: suspend BluetoothSocket.() -> Unit) {
    val socket = socketLock.withLock {
      var socket = socket
      if (socket != null && socket.isConnected)
        return@withLock socket

      closeCurrentSocketImpl(null)

      socket = device.createRfcommSocketToServiceRecord(CLIENT_ID)

      val connectComplete = CompletableDeferred<Unit>()
      par(
        {
          logger.log { "connect ${device.debugName()}" }
          try {
            var attempt = 0
            while (attempt < 5) {
              try {
                val duration = measureTimeMillis {
                  socket.connect()
                }
                if (socket.isConnected) {
                  logger.log { "connected to ${device.debugName()} in ${duration}ms" }
                  break
                }
              } catch (e: Throwable) {
                logger.log { "connect failed ${device.debugName()} attempt $attempt" }
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
              logger.log { "cancel connect ${device.debugName()}" }
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
