/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.state.action
import com.ivianuu.essentials.state.bind
import com.ivianuu.essentials.ui.dialog.Dialog
import com.ivianuu.essentials.ui.dialog.DialogScaffold
import com.ivianuu.essentials.ui.material.fixedStepPolicy
import com.ivianuu.essentials.ui.navigation.KeyUiContext
import com.ivianuu.essentials.ui.navigation.Model
import com.ivianuu.essentials.ui.navigation.ModelKeyUi
import com.ivianuu.essentials.ui.navigation.PopupKey
import com.ivianuu.essentials.ui.prefs.SliderListItem
import com.ivianuu.injekt.Provide
import com.ivianuu.minirig.data.MinirigPrefs
import com.ivianuu.minirig.domain.MinirigRepository
import kotlinx.coroutines.flow.map

data class ChannelKey(val address: String) : PopupKey<Unit>

@Provide val channelUi = ModelKeyUi<ChannelKey, ChannelModel> {
  DialogScaffold {
    Dialog(
      title = { Text(name) },
      content = {
        Column {
          SliderListItem(
            value = channel,
            onValueChange = updateChannel,
            title = { Text("Channel") },
            valueRange = 0f..1f,
            stepPolicy = fixedStepPolicy(1)
          )

          SliderListItem(
            value = auxChannel,
            onValueChange = updateAuxChannel,
            title = { Text("Aux channel") },
            valueRange = 0f..1f,
            stepPolicy = fixedStepPolicy(1)
          )
        }
      }
    )
  }
}

data class ChannelModel(
  val name: String,
  val channel: Float,
  val updateChannel: (Float) -> Unit,
  val auxChannel: Float,
  val updateAuxChannel: (Float) -> Unit
)

@Provide fun channelModel(
  minirigRepository: MinirigRepository,
  pref: DataStore<MinirigPrefs>,
  ctx: KeyUiContext<ChannelKey>
) = Model<ChannelModel> {
  val prefs = pref.data.bind(MinirigPrefs())
  val name = minirigRepository.minirig(ctx.key.address)
    .map { it?.name }
    .bind(null) ?: ""

  ChannelModel(
    name = name,
    channel = prefs.channel[ctx.key.address] ?: 0.5f,
    updateChannel = action { value ->
      pref.updateData {
        copy(
          channel = channel.toMutableMap().apply { put(ctx.key.address, value) })
      }
    },
    auxChannel = prefs.auxChannel[ctx.key.address] ?: 0.5f,
    updateAuxChannel = action { value ->
      pref.updateData {
        copy(
          auxChannel = auxChannel.toMutableMap().apply { put(ctx.key.address, value) })
      }
    }
  )
}
