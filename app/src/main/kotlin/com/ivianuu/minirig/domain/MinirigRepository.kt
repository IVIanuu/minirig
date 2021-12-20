/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.cache.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.time.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.common.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Provide @Scoped<AppScope> class MinirigRepository(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val context: IOContext,
  private val remote: MinirigRemote,
  private val L: Logger,
  private val scope: NamedCoroutineScope<AppScope>
) {
  val minirigs: Flow<List<Minirig>>
    get() = remote.bondedDeviceChanges()
      .onStart<Any> { emit(Unit) }
      .map {
        bluetoothManager.adapter?.bondedDevices
          ?.filter { it.address.isMinirigAddress() }
          ?.map { it.toMinirig() }
          ?: emptyList()
      }
      .distinctUntilChanged()
      .flowOn(context)

  fun minirig(address: String): Flow<Minirig?> = remote.bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      bluetoothManager.adapter
        ?.getRemoteDevice(address)
        ?.toMinirig()
    }
    .flowOn(context)

  private val states = Cache<String, Flow<MinirigState>>()

  fun minirigState(address: String): Flow<MinirigState> = flow {
    emitAll(
      states.get(address) {
        merge(
          timer(10.seconds),
          remote.bondedDeviceChanges()
        )
          .mapLatest { readMinirigState(address) }
          .distinctUntilChanged()
          .flowOn(context)
          .shareIn(scope, SharingStarted.WhileSubscribed(), 1)
          .let { sharedFlow ->
            sharedFlow
              .onStart {
                if (sharedFlow.replayCache.isEmpty())
                  emit(MinirigState(false))
              }
          }
      }
    )
  }

  private suspend fun readMinirigState(address: String, @Inject L: Logger): MinirigState =
    remote.withMinirig(address, "read minirig state $address") {
      // sending this message triggers the state output
      catch { send("BGET_BATTERY") }

      val batteryPercentage = withTimeoutOrNull(PingPongTimeout) {
        messages
          .mapNotNull { message ->
            if (!message.startsWith("B")) return@mapNotNull null
            message
              .removePrefix("B")
              .take(5)
              .toIntOrNull()
              ?.toBatteryPercentage()
          }
          .first()
      }

      catch { send("xGET_STATUS") }

      val linkupState = withTimeoutOrNull(PingPongTimeout) {
        messages
          .mapNotNull { message ->
            message
              .takeIf { it.length >= 36 }
              ?.substring(35, 36)
              ?.let {
                when (it) {
                  "1", "2", "3", "4" -> LinkupState.SLAVE
                  "5", "6", "7", "8" -> LinkupState.MASTER
                  else -> LinkupState.NONE
                }
              }
          }
          .first()
      } ?: LinkupState.NONE

      return@withMinirig MinirigState(
        isConnected = true,
        batteryPercentage = batteryPercentage,
        linkupState = linkupState
      )
    } ?: MinirigState(isConnected = false)
}

private fun Int.toBatteryPercentage(): Float {
  val voltageRange = 10200..12400
  return ((this - voltageRange.first) * 100) / (voltageRange.last - voltageRange.first) / 100f
}
