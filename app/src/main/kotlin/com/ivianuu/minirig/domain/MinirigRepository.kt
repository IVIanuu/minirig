/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Provide class MinirigRepository(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val remote: MinirigRemote,
  private val L: Logger
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

  fun minirig(address: String): Flow<Minirig?> = remote.bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      bluetoothManager.adapter
        ?.getRemoteDevice(address)
        ?.toMinirig()
    }

  fun minirigState(address: String) = remote.bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .transformLatest {
      remote.withMinirig(address) {
        var batteryPercentage = 0f

        suspend fun emit() {
          emit(MinirigState(isConnected = true, batteryPercentage = batteryPercentage))
        }

        par(
          {
            messages.collect { message ->
              when {
                message.startsWith("B") -> {
                  val newBatteryPercentage = message
                    .removePrefix("B")
                    .take(5)
                    .toInt()
                    .toBatteryPercentage()

                  if (newBatteryPercentage != batteryPercentage) {
                    batteryPercentage = newBatteryPercentage
                    emit()
                  }
                }
              }
            }
          },
          {
            while (currentCoroutineContext().isActive) {
              catch { send("BGET_BATTERY") }
              delay(5000)
            }
          }
        )
      } ?: emit(MinirigState(isConnected = false))
    }
    .distinctUntilChanged()
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
