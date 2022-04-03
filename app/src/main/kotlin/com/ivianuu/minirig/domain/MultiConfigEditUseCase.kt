/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.coroutines.guarantee
import com.ivianuu.essentials.coroutines.parForEach
import com.ivianuu.essentials.coroutines.parMap
import com.ivianuu.essentials.coroutines.race
import com.ivianuu.essentials.time.milliseconds
import com.ivianuu.essentials.ui.navigation.Navigator
import com.ivianuu.essentials.ui.navigation.push
import com.ivianuu.injekt.Provide
import com.ivianuu.minirig.data.MinirigConfig
import com.ivianuu.minirig.data.merge
import com.ivianuu.minirig.ui.ConfigKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

fun interface MultiConfigEditUseCase : suspend (List<String>) -> Unit

@Provide fun multiConfigEditUseCase(
  configRepository: ConfigRepository,
  navigator: Navigator
) = MultiConfigEditUseCase { minirigAddresses ->
  val tmpConfigId = "Tmp"

  race(
    {
      guarantee(
        block = { navigator.push(ConfigKey(tmpConfigId)) },
        finalizer = {
          delay(500.milliseconds) // wait for ui to complete exit animation
          configRepository.deleteConfig(tmpConfigId)
        }
      )
    },
    {
      val initialConfig = minirigAddresses
        .parMap { configRepository.config(it).first()!! }
        .merge(tmpConfigId)

      configRepository.updateConfig(initialConfig)

      minirigAddresses.parForEach { minirigAddress ->
        var latestConfig = initialConfig
        configRepository.config(tmpConfigId)
          .filterNotNull()
          .mapNotNull { currentConfig ->
            (if (currentConfig == latestConfig) null
            else buildList<MinirigConfig.() -> MinirigConfig> {
              if (currentConfig.band1 != latestConfig.band1)
                add { copy(band1 = currentConfig.band1) }
              if (currentConfig.band2 != latestConfig.band2)
                add { copy(band2 = currentConfig.band2) }
              if (currentConfig.band3 != latestConfig.band3)
                add { copy(band3 = currentConfig.band3) }
              if (currentConfig.band4 != latestConfig.band4)
                add { copy(band4 = currentConfig.band4) }
              if (currentConfig.band5 != latestConfig.band5)
                add { copy(band5 = currentConfig.band5) }
              if (currentConfig.bassBoost != latestConfig.bassBoost)
                add { copy(bassBoost = currentConfig.bassBoost) }
              if (currentConfig.loud != latestConfig.loud)
                add { copy(loud = currentConfig.loud) }
              if (currentConfig.gain != latestConfig.gain)
                add { copy(gain = currentConfig.gain) }
              if (currentConfig.auxGain != latestConfig.auxGain)
                add { copy(auxGain = currentConfig.auxGain) }
              if (currentConfig.channel != latestConfig.channel)
                add { copy(channel = currentConfig.channel) }
              if (currentConfig.auxChannel != latestConfig.auxChannel)
                add { copy(auxChannel = currentConfig.auxChannel) }
            }).also { latestConfig = currentConfig }
          }
          .collect { reducers ->
            configRepository.updateConfig(
              reducers.fold(configRepository.config(minirigAddress).first()!!) { acc, next ->
                acc.next()
              }
            )
          }
      }
    }
  )
}
