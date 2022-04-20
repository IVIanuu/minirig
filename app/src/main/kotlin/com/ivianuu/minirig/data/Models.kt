/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.data

import android.bluetooth.BluetoothDevice
import com.ivianuu.essentials.android.prefs.DataStoreModule
import com.ivianuu.injekt.Provide
import kotlinx.serialization.Serializable

data class Minirig(val address: String, val name: String)

fun BluetoothDevice.toMinirig() = Minirig(address, alias ?: name)

fun String.isMinirigAddress() = startsWith("00:12:6F")

fun BluetoothDevice.debugName() = "[${alias ?: name} ~ $address]"

fun Minirig.debugName() = "[$name ~ $address]"

@Serializable data class MinirigConfig(
  val id: String,
  val band1: Float = 0.5f,
  val band2: Float = 0.5f,
  val band3: Float = 0.5f,
  val band4: Float = 0.5f,
  val band5: Float = 0.5f,
  val bassBoost: Float = 0.8f,
  val loud: Boolean = false,
  val gain: Float = 0.4f,
  val auxGain: Float = 1f
) {
  companion object {
    @Provide val dataStoreModule = DataStoreModule<List<MinirigConfig>>("configs") {
      emptyList()
    }
  }
}

fun MinirigConfig.apply(other: MinirigConfig) = other.copy(id = id)

fun MinirigConfig.applyEq(other: MinirigConfig) = other.copy(
  id = id,
  bassBoost = bassBoost,
  loud = loud,
  gain = gain,
  auxGain = auxGain
)

fun MinirigConfig.applyGain(other: MinirigConfig) = other.copy(
  id = id,
  band1 = band1,
  band2 = band2,
  band3 = band3,
  band4 = band4,
  band5 = band5
)

fun List<MinirigConfig>.merge(id: String) = MinirigConfig(
  id = id,
  band1 = map { it.band1 }.average().toFloat(),
  band2 = map { it.band2 }.average().toFloat(),
  band3 = map { it.band3 }.average().toFloat(),
  band4 = map { it.band4 }.average().toFloat(),
  band5 = map { it.band5 }.average().toFloat(),
  bassBoost = map { it.bassBoost }.average().toFloat(),
  loud = all { it.loud },
  gain = map { it.gain }.average().toFloat(),
  auxGain = map { it.auxGain }.average().toFloat()
)

data class MinirigState(
  val isConnected: Boolean = false,
  val batteryPercentage: Float? = null,
  val linkupState: LinkupState = LinkupState.NONE,
  val powerState: PowerState = PowerState.NORMAL
)

enum class LinkupState {
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
