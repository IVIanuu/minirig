/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.material.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.dialog.*
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.injekt.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SoundTestKey(val addresses: List<String>) : DialogKey<Unit>

@Provide val soundTestUi = ModelKeyUi<SoundTestKey, SoundTestModel> {
  DialogScaffold {
    Dialog(
      title = { Text("Sound test") },
      content = { Text("Playing: ${model.playing}, with sub ${model.withSub}") },
      buttons = {
        TextButton(onClick = model.finish) {
          Text("Finish")
        }
      }
    )
  }
}

data class SoundTestModel(
  val playing: String?,
  val withSub: Boolean,
  val finish: () -> Unit
)

@Provide fun soundTestModel(
  configRepository: ConfigRepository,
  minirigRepository: MinirigRepository,
  ctx: KeyUiContext<SoundTestKey>
): SoundTestModel {
  var playing: String? by memo { stateVar(null) }
  var withSub by memo { stateVar(false) }

  memoLaunch {
    val initialConfigs = ctx.key.addresses
      .map { configRepository.config(it).first()!! }

    guarantee(
      block = {
        while (true) {
          for (playingAddress in ctx.key.addresses) {
            playing = minirigRepository.minirig(playingAddress).first()!!.name
            par(
              *ctx.key.addresses
                .map { address ->
                  suspend {
                    if (address == playingAddress) {
                      configRepository.updateConfig(
                        configRepository.config(address).first()!!
                          .copy(gain = 0.5f)
                      )
                      withSub = false

                      delay(3000)

                      configRepository.updateConfig(
                        configRepository.config(address).first()!!
                          .copy(gain = 0.5f, auxGain = 0.5f)
                      )
                      withSub = true
                    } else {
                      configRepository.updateConfig(
                        configRepository.config(address).first()!!
                          .copy(gain = 0f, auxGain = 0f)
                      )
                    }
                  }
                }
                .toTypedArray()
            )

            delay(5000)
          }
        }
      },
      finalizer = {
        initialConfigs.forEach { configRepository.updateConfig(it) }
      }
    )
  }

  return SoundTestModel(
    playing = playing,
    withSub = withSub,
    finish = action { ctx.navigator.pop(ctx.key) }
  )
}
