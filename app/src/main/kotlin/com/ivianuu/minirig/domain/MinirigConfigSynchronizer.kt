/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.*
import com.ivianuu.essentials.app.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.util.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Provide fun minirigConfigSynchronizer(
  context: IOContext,
  configRepository: ConfigRepository,
  minirigRepository: MinirigRepository,
  remote: MinirigRemote,
  L: Logger,
  T: ToastContext
) = ScopeWorker<AppScope> {
  withContext(context) {
    minirigRepository.minirigs.collectLatest { minirigs ->
      minirigs.parForEach { minirig ->
        remote.isConnected(minirig.address).collectLatest { isConnected ->
          if (isConnected) {
            log { "observe config changes ${minirig.debugName()}" }
            configRepository.config(minirig.address).collectLatest { config ->
              log { "apply config ${minirig.debugName()}" }
              suspend fun applyConfig(attempt: Int) {
                catch {
                  applyConfig(config!!)
                }.onFailure {
                  log { "failed to apply config to ${minirig.debugName()} -> ${it.asLog()}" }
                  showToast("Could not apply config to ${minirig.debugName()}")
                  delay(2000)
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
  config: MinirigConfig,
  @Inject L: Logger,
  remote: MinirigRemote
) {
  remote.withMinirig(config.id) {
    log { "${config.id} apply config $config" }

    val currentConfig = readMinirigConfig()

    log { "${config.id} current config $currentConfig" }

    suspend fun updateConfigIfNeeded(key: Int, value: Int) {
      // format key and value to match the minirig format
      var finalKey = key.toString()
      if (finalKey.length == 1)
        finalKey = "0$finalKey"

      var finalValue = value.toString()
      if (finalValue.length == 1)
        finalValue = "0$finalValue"

      // only write if the value has changed
      if (currentConfig[key] != value) {
        log { "${device.debugName()} update $finalKey -> $finalValue" }
        send("q p $finalKey $finalValue")
      }
    }

    updateConfigIfNeeded(
      8,
      // > 30 means mutes the minirig
      if (config.gain == 0f) 31
      // minirig value range is 0..30 and 30 means lowest gain
      else (30 * (1f - config.gain)).toInt()
    )

    updateConfigIfNeeded(
      9,
      // > 10 means mutes the aux device
      if (config.auxGain == 0f) 11
      // minirig value range is 0..10 and 10 means highest gain
      else (10 * config.auxGain).toInt()
    )

    updateConfigIfNeeded(1, (config.band1 * 99).toInt())
    updateConfigIfNeeded(2, (config.band2 * 99).toInt())
    updateConfigIfNeeded(3, (config.band3 * 99).toInt())
    updateConfigIfNeeded(4, (config.band4 * 99).toInt())
    updateConfigIfNeeded(5, (config.band5 * 99).toInt())

    updateConfigIfNeeded(
      14,
      ((1f - config.channel) * 99).toInt()
    )

    updateConfigIfNeeded(
      15,
      ((1f - config.auxChannel) * 99).toInt()
    )

    updateConfigIfNeeded(
      7,
      // everything above 10 sounds not healthy
      (10 * config.bassBoost).toInt()
    )

    updateConfigIfNeeded(
      12,
      if (config.loud) 1 else 0
    )
  }
}

private suspend fun MinirigSocket.readMinirigConfig(@Inject L: Logger): Map<Int, Int> {
  // sending this message triggers the state output
  send("q p 00 50")
  return messages
    .first { it.startsWith("q") }
    .removePrefix("q ")
    .split(" ")
    .withIndex()
    .associateBy { it.index + 1 }
    .mapValues { it.value.value.toInt() }
}
