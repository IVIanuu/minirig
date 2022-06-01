/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.github.michaelbull.result.onFailure
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.catch
import com.ivianuu.essentials.coroutines.parForEach
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.asLog
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.util.ToastContext
import com.ivianuu.essentials.util.showToast
import com.ivianuu.injekt.Inject
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.coroutines.IOContext
import com.ivianuu.minirig.data.MinirigPrefs
import com.ivianuu.minirig.data.debugName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Provide fun minirigConfigSynchronizer(
  context: IOContext,
  maximumVolume: Flow<MaximumVolume>,
  minirigRepository: MinirigRepository,
  pref: DataStore<MinirigPrefs>,
  remote: MinirigRemote,
  L: Logger,
  T: ToastContext
) = ScopeWorker<AppScope> {
  withContext(context) {
    combine(minirigRepository.minirigs, pref.data, maximumVolume) { a, b, c -> Triple(a, b, c) }
      .collectLatest { (minirigs, prefs, maximumVolume) ->
        minirigs.parForEach { minirig ->
          remote.isConnected(minirig.address).collectLatest { isConnected ->
            if (isConnected) {
              log { "observe config changes ${minirig.debugName()}" }
              suspend fun applyConfig(attempt: Int) {
                log { "apply config ${minirig.debugName()} attempt $attempt" }
                if (attempt == 5) {
                  showToast("Could not apply config to ${minirig.debugName()}")
                }

                catch {
                  applyConfig(minirig.address, prefs, maximumVolume.value)
                }.onFailure {
                  log { "failed to apply config to ${minirig.debugName()} $attempt -> ${it.asLog()}" }
                  delay(RetryDelay)
                  applyConfig(attempt + 1)
                }
              }

              applyConfig(0)
            } else {
              log { "ignore config changes ${minirig.debugName()}" }
            }
          }
        }
      }
  }
}

private val lastMonoByDevice = mutableMapOf<String, Boolean>()
private val lastMonoLock = Mutex()

private suspend fun applyConfig(
  address: String,
  prefs: MinirigPrefs,
  maximumVolume: Boolean,
  @Inject L: Logger,
  @Inject remote: MinirigRemote
) {
  remote.withMinirig(address) {
    log { "${device.debugName()} apply config $prefs" }

    val currentConfig = readMinirigConfig()

    log { "${device.debugName()} current config $currentConfig" }

    val monoChanged = lastMonoLock.withLock {
      if (prefs.mono && lastMonoByDevice[address] != true) {
        lastMonoByDevice[address] = true
        send("M")
        true
      } else if (lastMonoByDevice[address] != false) {
        lastMonoByDevice[address] = false
        send("R")
        true
      } else false
    }

    if (monoChanged)
      delay(500)

    suspend fun updateConfigIfNeeded(key: Int, value: Int) {
      // format key and value to match the minirig format
      var finalKey = key.toString()
      if (finalKey.length == 1)
        finalKey = "0$finalKey"

      var finalValue = value.toString()
      if (finalValue.length == 1)
        finalValue = "0$finalValue"

      // only write if the value has changed
      if (monoChanged || currentConfig[key] != value) {
        log { "${device.debugName()} update $finalKey -> $finalValue current was ${currentConfig[key]}" }
        send("q p $finalKey $finalValue")
      }
    }

    // enable loud mode if android device is on maximum volume
    updateConfigIfNeeded(12, if (maximumVolume) 1 else 0)

    updateConfigIfNeeded(8, if (prefs.transmitter) 31 else 0)
    updateConfigIfNeeded(9, 10)

    suspend fun updateEqBandIfNeeded(key: Int, value: Float) {
      updateConfigIfNeeded(
        key,
        (value * 100)
          .toInt()
          .coerceIn(1, 99)
      )
    }

    updateEqBandIfNeeded(1, prefs.band1)
    updateEqBandIfNeeded(2, prefs.band2)
    updateEqBandIfNeeded(3, prefs.band3)
    updateEqBandIfNeeded(4, prefs.band4)
    updateEqBandIfNeeded(5, prefs.band5)

    // disable bass boost
    updateConfigIfNeeded(7, 0)
  }
}

private suspend fun MinirigSocket.readMinirigConfig(@Inject L: Logger): Map<Int, Int> {
  // sending this message triggers the state output
  send("q p 00 50")
  return withTimeoutOrNull(PingPongTimeout) {
    messages
      .first { it.startsWith("q") }
      .parseEq()
  } ?: error("could not get minirig config from ${device.debugName()}")
}

fun String.parseEq(): Map<Int, Int> = removePrefix("q ")
  .split(" ")
  .withIndex()
  .associateBy { it.index + 1 }
  .mapValues { it.value.value.toIntOrNull() ?: 0 }
