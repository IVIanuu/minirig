/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig

import android.bluetooth.BluetoothDevice
import com.ivianuu.essentials.data.DataStoreModule
import com.ivianuu.injekt.Provide
import kotlinx.serialization.Serializable

data class Minirig(val address: String, val name: String)

fun BluetoothDevice.toMinirig() = Minirig(address, alias ?: name)

fun BluetoothDevice.isMinirig() = (alias ?: name)?.let {
  it.contains("minirig", ignoreCase = true) ||
      it.contains("minrig", ignoreCase = true)
} == true

fun BluetoothDevice.debugName() = "[${alias ?: name} ~ $address]"

fun Minirig.debugName() = "[$name ~ $address]"

@Serializable data class MinirigConfig(
  val minirigGain: Float = 1f,
  val auxGain: Float = 1f,
  val bassBoost: Int = 7,
  val loud: Boolean = false
)

fun List<MinirigConfig>.merge(): MinirigConfig = when {
  isEmpty() -> MinirigConfig()
  size == 1 -> single()
  else -> MinirigConfig(
    minirigGain = map { it.minirigGain }.average().toFloat(),
    auxGain = map { it.auxGain }.average().toFloat(),
    bassBoost = map { it.bassBoost }.average().toInt(),
    loud = all { it.loud }
  )
}

@Serializable data class MinirigPrefs(
  val configs: Map<String, MinirigConfig> = emptyMap(),
  val selectedMinirigs: Set<String> = emptySet()
) {
  @Provide companion object {
    @Provide val dataStoreModule = DataStoreModule("minirig_prefs") { MinirigPrefs() }
  }
}

data class MinirigState(
  val isConnected: Boolean = false,
  val batteryPercentage: Float? = null,
  val powerState: PowerState = PowerState.NORMAL,
  val twsState: TwsState = TwsState.NONE
)

enum class PowerState { NORMAL, CHARGING, POWER_OUT }

enum class TwsState { NONE, MASTER, SLAVE }
