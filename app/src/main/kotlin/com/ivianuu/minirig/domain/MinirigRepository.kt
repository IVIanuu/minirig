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
          timer(5.seconds),
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

      var linkupState = LinkupState.NONE
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
            "1", "2", "3", "4" -> LinkupState.SLAVE
            "5", "6", "7", "8" -> LinkupState.MASTER
            else -> LinkupState.NONE
          }
        }
      }

      return@withMinirig MinirigState(
        isConnected = true,
        batteryPercentage = batteryPercentage,
        linkupState = linkupState,
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
