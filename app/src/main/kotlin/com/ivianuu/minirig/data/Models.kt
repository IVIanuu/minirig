/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.data

import android.bluetooth.BluetoothDevice
import com.ivianuu.essentials.android.prefs.PrefModule
import com.ivianuu.injekt.Provide
import kotlinx.serialization.Serializable

data class Minirig(val address: String, val name: String)

fun BluetoothDevice.toMinirig() = Minirig(address, alias ?: name)

fun BluetoothDevice.isMinirig() = (alias ?: name).contains("minirig", ignoreCase = true)

fun BluetoothDevice.debugName() = "[${alias ?: name} ~ $address]"

fun Minirig.debugName() = "[$name ~ $address]"

@Serializable data class MinirigPrefs(
  val band1: Float = 0.5f,
  val band2: Float = 0.5f,
  val band3: Float = 0.5f,
  val band4: Float = 0.5f,
  val band5: Float = 0.5f,
  val bassBoost: Boolean = true,
  val loud: Boolean = false,
  val gain: Float = 0.4f,
  val auxGain: Float = 1f
) {
  companion object {
    @Provide val prefModule = PrefModule { MinirigPrefs() }
  }
}

data class MinirigState(
  val isConnected: Boolean = false,
  val batteryPercentage: Float? = null,
  val twsState: TwsState = TwsState.NONE,
  val powerState: PowerState = PowerState.NORMAL
)

enum class TwsState {
  NONE,
  MASTER,
  SLAVE
}

enum class PowerState {
  NORMAL, CHARGING, POWER_OUT
}

data class MinirigRuntimeData(
  val minsSinceLastCharge: Int,
  val minsInStandbySinceLastCharge: Int,
  //val minsOnCharge: Int,
  val chargeCablesInserted: Int
)

fun MinirigRuntimeData(
  runtimeData1: String,
  runtimeData2: String
): MinirigRuntimeData = MinirigRuntimeData(
  minsSinceLastCharge = runtimeData1.substring(2, 6).toInt(),
  minsInStandbySinceLastCharge = runtimeData1.substring(7, 11).toInt(),
  //minsOnCharge = runtimeData1.substring(57, 65).toInt(),
  chargeCablesInserted = runtimeData1.substring(114, 119).toInt()
)
