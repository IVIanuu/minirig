/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.BluetoothManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.catch
import com.ivianuu.essentials.coroutines.par
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.permission.PermissionState
import com.ivianuu.essentials.state.state
import com.ivianuu.essentials.time.seconds
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import com.ivianuu.injekt.common.Scoped
import com.ivianuu.injekt.coroutines.IOContext
import com.ivianuu.injekt.coroutines.NamedCoroutineScope
import com.ivianuu.minirig.data.Minirig
import com.ivianuu.minirig.data.MinirigState
import com.ivianuu.minirig.data.PowerState
import com.ivianuu.minirig.data.TwsState
import com.ivianuu.minirig.data.isMinirig
import com.ivianuu.minirig.data.toMinirig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Provide @Scoped<AppScope> class MinirigRepository(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val context: IOContext,
  private val remote: MinirigRemote,
  private val logger: Logger,
  private val permissionState: Flow<PermissionState<MinirigBluetoothConnectPermission>>,
  private val scope: NamedCoroutineScope<AppScope>
) {
  val minirigs: Flow<List<Minirig>>
    get() = permissionState
      .flatMapLatest {
        if (!it) flowOf(emptyList())
        else remote.bondedDeviceChanges()
          .onStart<Any> { emit(Unit) }
          .map {
            bluetoothManager.adapter?.bondedDevices
              ?.filter { it.isMinirig() }
              ?.map { it.toMinirig() }
              ?: emptyList()
          }
          .distinctUntilChanged()
          .flowOn(context)
      }

  fun minirig(address: String): Flow<Minirig?> = remote.bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      bluetoothManager.adapter
        ?.getRemoteDevice(address)
        ?.toMinirig()
    }
    .flowOn(context)

  private val states = mutableMapOf<String, Flow<MinirigState>>()
  private val statesLock = Mutex()

  fun minirigState(address: String): Flow<MinirigState> = flow {
    emitAll(
      statesLock.withLock {
        states.getOrPut(address) {
          scope.state {
            var batteryPercentage: Float? by remember { mutableStateOf(null) }
            var powerState by remember { mutableStateOf(PowerState.NORMAL) }
            var twsState by remember { mutableStateOf(TwsState.NONE) }

            LaunchedEffect(true) {
              merge(
                flow<Unit> {
                  while (true) {
                    emit(Unit)
                    delay(5.seconds)
                  }
                },
                remote.bondedDeviceChanges()
              )
                .collect {
                  remote.withMinirig(address) {
                    par(
                      { catch { send("x") } },
                      { catch { send("B") } }
                    )
                  }
                }
            }

            LaunchedEffect(true) {
              remote.withMinirig(address) {
                messages
                  .filter { it.startsWith("x ") }
                  .collect { status ->
                    if (status.length >= 9) {
                      powerState = when (status.substring(8, 9)) {
                        "1" -> PowerState.NORMAL
                        "2" -> PowerState.CHARGING
                        else -> PowerState.NORMAL
                      }
                    }

                    if (status.length >= 7) {
                      twsState = when (status.substring(5, 7)) {
                        "30" -> TwsState.SLAVE
                        "31" -> TwsState.MASTER
                        else -> TwsState.NONE
                      }
                    }
                  }
              }
            }

            LaunchedEffect(true) {
              remote.withMinirig(address) {
                messages
                  .filter { it.startsWith("B") }
                  .collect { message ->
                    batteryPercentage = message
                      .removePrefix("B")
                      .take(5)
                      .toIntOrNull()
                      ?.toBatteryPercentage()
                  }
              }
            }

            MinirigState(
              isConnected = true,
              batteryPercentage = batteryPercentage,
              powerState = powerState,
              twsState = twsState
            )
          }
            .shareIn(scope, SharingStarted.WhileSubscribed(), 1)
            .let { sharedFlow ->
              sharedFlow
                .onStart {
                  if (sharedFlow.replayCache.isEmpty())
                    emit(MinirigState(false))
                }
            }
        }
      }
    )
  }
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
