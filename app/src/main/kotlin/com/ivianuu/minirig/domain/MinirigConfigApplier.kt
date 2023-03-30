/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.app.AppForegroundScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.coroutines.onCancel
import com.ivianuu.essentials.coroutines.parForEach
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.lerp
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.invoke
import com.ivianuu.essentials.time.seconds
import com.ivianuu.essentials.util.Toaster
import com.ivianuu.injekt.Inject
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.coroutines.IOContext
import com.ivianuu.minirig.data.MinirigConfig
import com.ivianuu.minirig.data.MinirigPrefs
import com.ivianuu.minirig.data.TwsState
import com.ivianuu.minirig.data.debugName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

@Provide fun minirigConfigApplier(
  context: IOContext,
  logger: Logger,
  pref: DataStore<MinirigPrefs>,
  repository: MinirigRepository,
  remote: MinirigRemote,
  toaster: Toaster
) = ScopeWorker<AppForegroundScope> {
  withContext(context) {
    repository.minirigs.collectLatest { minirigs ->
      minirigs.parForEach { minirig ->
        remote.isConnected(minirig.address).collectLatest { isConnected ->
          if (!isConnected) return@collectLatest

          remote.withMinirig(minirig.address) {
            val cache = mutableMapOf<Int, Int>()

            var lastTwsState: TwsState? = null

            remote.minirigState(minirig.address)
              .map { it.twsState }
              .distinctUntilChanged()
              .transformLatest { twsState ->
                if (lastTwsState == null) {
                  lastTwsState = twsState
                  emit(Unit)
                } else if (lastTwsState != TwsState.MASTER && twsState == TwsState.MASTER) {
                  lastTwsState = twsState

                  logger { "${device.debugName()} changed to tws master invalidate all after delay" }

                  delay(6.seconds)

                  logger { "${device.debugName()} invalidate all due to tws pairing" }

                  cache.clear()
                  emit(Unit)
                } else {
                  lastTwsState = twsState
                }
              }
              .flatMapLatest {
                pref.data
                  .map { it.configs[minirig.address] ?: MinirigConfig() }
                  .distinctUntilChanged()
              }
              .collectLatest { applyConfig(it, cache) }
          }
        }
      }
    }
  }
}

private suspend fun MinirigSocket.applyConfig(
  config: MinirigConfig,
  cache: MutableMap<Int, Int>,
  @Inject logger: Logger
) {
  logger { "${device.debugName()} apply config $config" }

  suspend fun updateConfigIfNeeded(tag: String, key: Int, value: Int) {
    fun Int.toMinirigFormat(): String {
      var tmp = toString()
      if (tmp.length == 1)
        tmp = "0$tmp"
      return tmp
    }

    val finalKey = key.toMinirigFormat()
    val finalValue = value.toMinirigFormat()

    // only write if the value has changed
    if (cache[key] != value) {
      onCancel(
        block = {
          logger { "${device.debugName()} apply $tag $finalKey -> $finalValue" }
          send("q p $finalKey $finalValue")
          cache[key] = value
          logger { "${device.debugName()} applied $tag $finalKey -> $finalValue" }
        },
        onCancel = {
          logger { "${device.debugName()} invalidate $tag $finalKey" }
          cache.remove(key)
        }
      )
    } else {
      logger { "${device.debugName()} skip $tag $finalKey $finalValue" }
    }
  }

  updateConfigIfNeeded(
    "minirig gain",
    8,
    // > 30 means mutes the minirig
    if (config.minirigGain == 0f) 31
    // minirig value range is 0..30 and 30 means lowest gain
    else (30 * (1f - config.minirigGain)).toInt()
  )

  updateConfigIfNeeded(
    "aux gain",
    9,
    // > 10 means mutes the aux device
    if (config.auxGain == 0f) 11
    // minirig value range is 0..10 and 10 means highest gain
    else (10 * config.auxGain).toInt()
  )

  updateConfigIfNeeded(
    "bass boost",
    7,
    if (!config.loud) config.bassBoost else -14
  )

  updateConfigIfNeeded(
    "loud",
    12,
    if (config.loud) 1 else 0
  )
}
