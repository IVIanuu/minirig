/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.OutlinedButton
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.time.*
import com.ivianuu.essentials.ui.dialog.*
import com.ivianuu.essentials.ui.material.*
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
      content = {
        Column {
          Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = "${model.playing} ${if (model.mode == SoundTestMode.SUBS) "sub" else ""} playing ${
              if (model.mode == SoundTestMode.MINIRIGS_AND_SUBS) "with sub"
              else ""
            }"
          )

          Subheader { Text("Mode") }
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            SoundTestMode.values().forEach { mode ->
              OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { model.updateMode(mode) },
                border = ButtonDefaults.outlinedBorder.copy(
                  brush = SolidColor(
                    value = if (mode == model.mode)
                      MaterialTheme.colors.secondary
                    else LocalContentColor.current.copy(alpha = 0.12f)
                  )
                )
              ) {
                Text(
                  text = mode.name.toLowerCase().capitalize(),
                  color = MaterialTheme.colors.onSurface
                    .copy(alpha = LocalContentAlpha.current)
                )
              }
            }
          }
        }
      }
    )
  }
}

data class SoundTestModel(
  val playing: String?,
  val mode: SoundTestMode,
  val updateMode: (SoundTestMode) -> Unit
)

enum class SoundTestMode {
  MINIRIGS_AND_SUBS, MINIRIGS, SUBS
}

@Provide fun soundTestModel(
  configRepository: ConfigRepository,
  minirigRepository: MinirigRepository,
  ctx: KeyUiContext<SoundTestKey>
): SoundTestModel {
  var playing: String? by memo { stateVar(null) }
  val mode = memo { MutableStateFlow(SoundTestMode.MINIRIGS_AND_SUBS) }

  memoLaunch {
    val initialConfigs = ctx.key.addresses
      .map { configRepository.config(it).first()!! }

    guarantee(
      block = {
        mode.collectLatest { currentMode ->
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
                            .copy(
                              gain = if (currentMode != SoundTestMode.SUBS) 0.5f else 0f,
                              auxGain = if (currentMode != SoundTestMode.MINIRIGS) 1f else 0f
                            )
                        )
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

              delay(PlayTime)
            }
          }
        }
      },
      finalizer = {
        initialConfigs.parForEach { configRepository.updateConfig(it) }
      }
    )
  }

  return SoundTestModel(
    playing = playing,
    mode = mode.bind(),
    updateMode = { value -> mode.value = value }
  )
}

private val PlayTime = 4.seconds