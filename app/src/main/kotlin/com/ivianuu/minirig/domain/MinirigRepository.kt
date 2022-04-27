/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.BluetoothManager
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.catch
import com.ivianuu.essentials.coroutines.EventFlow
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.permission.PermissionState
import com.ivianuu.essentials.time.seconds
import com.ivianuu.injekt.Inject
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Provide @Scoped<AppScope> class MinirigRepository(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val context: IOContext,
  private val remote: MinirigRemote,
  private val L: Logger,
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

  private val stateChanges = EventFlow<String>()

  suspend fun stateChanged(address: String) {
    stateChanges.emit(address)
  }

  fun minirigState(address: String): Flow<MinirigState> = flow {
    emitAll(
      statesLock.withLock {
        states.getOrPut(address) {
          merge(
            flow<Unit> {
              while (true) {
                emit(Unit)
                delay(5.seconds)
              }
            },
            remote.bondedDeviceChanges(),
            stateChanges.filter { it == address }
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
      }
    )
  }

  private suspend fun readMinirigState(address: String, @Inject L: Logger): MinirigState =
    remote.withMinirig(address) {
      // sending this message triggers the state output
      catch { send("B") }

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

      catch { send("x") }

      var linkupState = TwsState.NONE
      var powerState = PowerState.NORMAL

      withTimeoutOrNull(PingPongTimeout) {
        val status = messages
          .first { it.startsWith("x ") }

        if (status.length >= 9) {
          powerState = when (status.substring(8, 9)) {
            "1" -> PowerState.NORMAL
            "2" -> PowerState.CHARGING
            "3", "4" -> PowerState.POWER_OUT
            else -> PowerState.NORMAL
          }
        }

        if (status.length >= 36) {
          linkupState = when (status.substring(35, 36)) {
            "1", "2", "3", "4" -> TwsState.SLAVE
            "5", "6", "7", "8" -> TwsState.MASTER
            else -> TwsState.NONE
          }
        }
      }

      return@withMinirig MinirigState(
        isConnected = true,
        batteryPercentage = batteryPercentage,
        twsState = linkupState,
        powerState = powerState
      )
    } ?: MinirigState(isConnected = false)
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
