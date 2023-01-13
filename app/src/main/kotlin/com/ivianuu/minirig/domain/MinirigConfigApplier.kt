/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.app.AppForegroundScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.coroutines.parForEach
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.util.ToastContext
import com.ivianuu.injekt.Inject
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.coroutines.IOContext
import com.ivianuu.minirig.data.MinirigConfig
import com.ivianuu.minirig.data.MinirigPrefs
import com.ivianuu.minirig.data.debugName
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Provide fun minirigConfigApplier(
  context: IOContext,
  minirigRepository: MinirigRepository,
  pref: DataStore<MinirigPrefs>,
  remote: MinirigRemote,
  logger: Logger,
  T: ToastContext
) = ScopeWorker<AppForegroundScope> {
  withContext(context) {
    minirigRepository.minirigs.collectLatest { minirigs ->
      minirigs.parForEach { minirig ->
        remote.isConnected(minirig.address).collectLatest { isConnected ->
          if (!isConnected) return@collectLatest

          val cache = mutableMapOf<Int, Int>()

          pref.data
            .map { it.configs[minirig.address] ?: MinirigConfig() }
            .distinctUntilChanged()
            .collectLatest { applyConfig(minirig.address, it, cache) }
        }
      }
    }
  }
}

private suspend fun applyConfig(
  address: String,
  config: MinirigConfig,
  cache: MutableMap<Int, Int>,
  @Inject logger: Logger,
  @Inject remote: MinirigRemote
) {
  remote.withMinirig(address) {
    log { "${device.debugName()} apply config $config" }

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
        log { "${device.debugName()} update $tag $finalKey -> $finalValue" }
        send("q p $finalKey $finalValue")
        cache[key] = value
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
      if (!config.loud) config.bassBoost else 0
    )

    updateConfigIfNeeded(
      "loud",
      12,
      if (config.loud) 1 else 0
    )
  }
}
