/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.github.michaelbull.result.onFailure
import com.ivianuu.essentials.app.AppForegroundScope
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
import com.ivianuu.minirig.data.TwsState
import com.ivianuu.minirig.data.debugName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Provide fun minirigConfigSynchronizer(
  context: IOContext,
  minirigRepository: MinirigRepository,
  pref: DataStore<MinirigPrefs>,
  remote: MinirigRemote,
  logger: Logger,
  T: ToastContext
) = ScopeWorker<AppForegroundScope> {
  withContext(context) {
    combine(
      minirigRepository.minirigs,
      pref.data
    ) { minirigs, prefs -> minirigs to prefs }
      .collectLatest { (minirigs, prefs) ->
        minirigs.parForEach { minirig ->
          remote.isConnected(minirig.address).collectLatest { isConnected ->
            if (isConnected) {
              minirigRepository.minirigState(minirig.address)
                .filter { it.twsState == TwsState.MASTER }
                .debounce(10000)
                .map { true }
                .onStart { emit(false) }
                .collectLatest { forceUpdate ->
                  suspend fun applyConfig(attempt: Int) {
                    log { "apply config ${minirig.debugName()} attempt $attempt force $forceUpdate" }
                    if (attempt == 5) {
                      showToast("Could not apply config to ${minirig.debugName()}")
                    }

                    catch {
                      applyConfig(minirig.address, prefs, forceUpdate)
                    }.onFailure {
                      log { "failed to apply config to ${minirig.debugName()} $attempt -> ${it.asLog()}" }
                      delay(RetryDelay)
                      applyConfig(attempt + 1)
                    }
                  }

                  applyConfig(0)
                }
            } else {
              log { "ignore config changes ${minirig.debugName()}" }
            }
          }
        }
      }
  }
}

private suspend fun applyConfig(
  address: String,
  prefs: MinirigPrefs,
  forceUpdate: Boolean,
  @Inject logger: Logger,
  @Inject remote: MinirigRemote
) {
  remote.withMinirig(address) {
    log { "${device.debugName()} apply config $prefs force $forceUpdate" }

    val currentConfig = readMinirigConfig()

    log { "${device.debugName()} current config $currentConfig" }

    suspend fun updateConfigIfNeeded(key: Int, value: Int) {
      fun Int.toMinirigFormat(): String {
        var tmp = toString()
        if (tmp.length == 1)
          tmp = "0$tmp"
        return tmp
      }

      val finalKey = key.toMinirigFormat()
      val finalValue = value.toMinirigFormat()

      // only write if the value has changed
      if (forceUpdate || currentConfig[key] != value) {
        log { "${device.debugName()} update $finalKey -> $finalValue current was ${currentConfig[key]}" }
        send("q p $finalKey $finalValue")
      }
    }

    updateConfigIfNeeded(
      8,
      // > 30 means mutes the minirig
      if (prefs.minirigGain == 0f) 31
      // minirig value range is 0..30 and 30 means lowest gain
      else (30 * (1f - prefs.minirigGain)).toInt()
    )

    updateConfigIfNeeded(
      9,
      // > 10 means mutes the aux device
      if (prefs.auxGain == 0f) 11
      // minirig value range is 0..10 and 10 means highest gain
      else (10 * prefs.auxGain).toInt()
    )

    updateConfigIfNeeded(7, if (!prefs.loud && prefs.bassBoost) 4 else 0)
    updateConfigIfNeeded(12, if (prefs.loud) 1 else 0)

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
  }
}

private suspend fun MinirigSocket.readMinirigConfig(@Inject logger: Logger): Map<Int, Int> {
  // sending this message triggers the state output
  send("q p 00 50")
  return withTimeoutOrNull(PingPongTimeout) {
    messages
      .first { it.startsWith("q") }
      .parseEq()
  } ?: error("could not get minirig config from ${device.debugName()}")
}

private fun String.parseEq(): Map<Int, Int> = removePrefix("q ")
  .split(" ")
  .withIndex()
  .associateBy { it.index + 1 }
  .mapValues { it.value.value.toIntOrNull() ?: 0 }
