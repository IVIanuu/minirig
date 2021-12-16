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
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Provide class MinirigRepository(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val context: IOContext,
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
      .flowOn(context)

  fun minirig(address: String): Flow<Minirig?> = remote.bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      bluetoothManager.adapter
        ?.getRemoteDevice(address)
        ?.toMinirig()
    }
    .flowOn(context)

  fun minirigState(address: String) = remote.bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .transformLatest {
      remote.withMinirig(address) {
        var batteryPercentage = 0f
        var linkupState = LinkupState.NONE

        suspend fun emit() {
          emit(
            MinirigState(
              isConnected = true,
              batteryPercentage = batteryPercentage,
              linkupState = linkupState
            )
          )
        }

        emit()

        par(
          {
            messages.collect { message ->
              log { "received $message" }
              when {
                message.startsWith("B") -> {
                  val newBatteryPercentage = message
                    .removePrefix("B")
                    .take(5)
                    .toIntOrNull()
                    ?.toBatteryPercentage()
                    ?: return@collect

                  if (newBatteryPercentage != batteryPercentage) {
                    batteryPercentage = newBatteryPercentage
                    emit()
                  }
                }
                message.startsWith("x") -> {
                  val newLinkupState = message
                    .takeIf { it.length >= 36 }
                    ?.substring(35, 36)
                    ?.let {
                      when (it) {
                        "1", "2", "3", "4" -> LinkupState.SLAVE
                        "5", "6", "7", "8" -> LinkupState.MASTER
                        else -> LinkupState.NONE
                      }
                    } ?: LinkupState.NONE

                  if (newLinkupState != linkupState) {
                    linkupState = newLinkupState
                    emit()
                  }

                  log { "${device.debugName()} broadcast state -> $newLinkupState" }
                }
              }
            }
          },
          {
            while (currentCoroutineContext().isActive) {
              catch { send("BGET_BATTERY") }
              catch { send("xGET_STATUS") }
              delay(5000)
            }
          }
        )
      } ?: emit(MinirigState(isConnected = false))
    }
    .distinctUntilChanged()
    .flowOn(context)
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
