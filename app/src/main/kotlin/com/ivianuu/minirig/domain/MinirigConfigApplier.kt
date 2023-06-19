/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.ivianuu.essentials.app.AppForegroundScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.compose.launchComposition
import com.ivianuu.essentials.coroutines.ScopedCoroutineScope
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.util.Toaster
import com.ivianuu.injekt.Provide
import com.ivianuu.minirig.data.MinirigConfig
import com.ivianuu.minirig.data.MinirigPrefs
import com.ivianuu.minirig.data.TwsState
import com.ivianuu.minirig.data.debugName
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Provide fun minirigConfigApplier(
  logger: Logger,
  pref: DataStore<MinirigPrefs>,
  repository: MinirigRepository,
  remote: MinirigRemote,
  scope: ScopedCoroutineScope<AppForegroundScope>,
  toaster: Toaster
) = ScopeWorker<AppForegroundScope> {
  scope.launchComposition {
    val minirigs by remember {
      repository.minirigs
        .flatMapLatest { minirigs ->
          combine(
            minirigs
              .map { minirig ->
                remote.isConnected(minirig.address)
                  .map { minirig to it }
              }
          ) { minirigsWithState ->
            minirigsWithState
              .filter { it.second }
              .map { it.first }
          }
        }
    }.collectAsState(emptyList())

    minirigs.forEach { minirig ->
      key(minirig) {
        val (config, twsState) = remember {
          combine(
            pref.data
              .map { it.configs[minirig.address] ?: MinirigConfig() },
            remote.minirigState(minirig.address)
              .map { it.twsState }
          ) { config, twsState -> config to twsState }
        }.collectAsState(null to TwsState.NONE).value

        if (config != null) {
          @Composable fun MinirigConfigItem(tag: String, key: Int, value: Int) {
            LaunchedEffect(value, twsState) {
              fun Int.toMinirigFormat(): String {
                var tmp = toString()
                if (tmp.length == 1)
                  tmp = "0$tmp"
                return tmp
              }

              val finalKey = key.toMinirigFormat()
              val finalValue = value.toMinirigFormat()

              remote.withMinirig<Unit>(minirig.address) {
                logger.log { "${device.debugName()} apply $tag $finalKey -> $finalValue" }
                send("q p $finalKey $finalValue")
              }
            }
          }

          MinirigConfigItem(
            tag = "minirig gain",
            key = 8,
            // > 30 means mutes the minirig
            value = if (config.minirigGain == 0f) 31
            // minirig value range is 0..30 and 30 means lowest gain
            else (30 * (1f - config.minirigGain)).toInt()
          )

          MinirigConfigItem(
            tag = "aux gain",
            key = 9,
            // > 10 means mutes the aux device
            value = if (config.auxGain == 0f) 11
            // minirig value range is 0..10 and 10 means highest gain
            else (10 * config.auxGain).toInt()
          )

          MinirigConfigItem(
            tag = "bass boost",
            key = 7,
            value = config.bassBoost
          )

          MinirigConfigItem(
            tag = "loud",
            key = 12,
            value = if (config.loud) 1 else 0
          )
        }
      }
    }
  }
}
