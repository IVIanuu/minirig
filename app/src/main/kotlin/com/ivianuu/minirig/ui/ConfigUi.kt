/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.ivianuu.essentials.resource.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.common.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.essentials.ui.popup.*
import com.ivianuu.essentials.ui.prefs.*
import com.ivianuu.injekt.*
import com.ivianuu.minirig.data.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.flow.*

data class ConfigKey(val id: String) : Key<Unit>

@Provide val configUi = ModelKeyUi<ConfigKey, ConfigModel> {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(name) },
        actions = {
          PopupMenuButton(
            items = listOf(
              PopupMenu.Item(onSelected = saveAs) { Text("Save as") }
            )
          )
        }
      )
    }
  ) {
    VerticalList {
      item {
        Subheader { Text("Equalizer") }
      }

      item {
        SliderListItem(
          value = band1,
          onValueChange = updateBand1,
          title = { Text("70Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        SliderListItem(
          value = band2,
          onValueChange = updateBand2,
          title = { Text("250Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        SliderListItem(
          value = band3,
          onValueChange = updateBand3,
          title = { Text("850Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        SliderListItem(
          value = band4,
          onValueChange = updateBand4,
          title = { Text("3KHz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        SliderListItem(
          value = band5,
          onValueChange = updateBand5,
          title = { Text("10.5KHz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        Subheader { Text("Gain") }
      }

      item {
        SliderListItem(
          value = bassBoost,
          onValueChange = updateBassBoost,
          title = { Text("Bass boost") },
          stepPolicy = incrementingStepPolicy(0.1f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        SwitchListItem(
          value = loud,
          onValueChange = updateLoud,
          title = { Text("Loud") }
        )
      }

      item {
        SliderListItem(
          value = gain,
          onValueChange = updateGain,
          title = { Text("Gain") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        SliderListItem(
          value = auxGain,
          onValueChange = updateAuxGain,
          title = { Text("Aux gain") },
          stepPolicy = incrementingStepPolicy(0.1f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        Subheader { Text("Channel") }
      }

      @Composable fun ChannelListItem(
        value: Float,
        onValueChange: (Float) -> Unit,
        title: String
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("L")

          SliderListItem(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            title = { Text(title) },
            stepPolicy = incrementingStepPolicy(0.5f)
          )

          Text("R")
        }
      }

      item {
        ChannelListItem(
          value = channel,
          onValueChange = updateChannel,
          title = "Channel",
        )
      }

      item {
        ChannelListItem(
          value = auxChannel,
          onValueChange = updateAuxChannel,
          title = "Aux channel",
        )
      }
    }
  }
}

data class ConfigModel(
  val name: String,
  val band1: Float,
  val updateBand1: (Float) -> Unit,
  val band2: Float,
  val updateBand2: (Float) -> Unit,
  val band3: Float,
  val updateBand3: (Float) -> Unit,
  val band4: Float,
  val updateBand4: (Float) -> Unit,
  val band5: Float,
  val updateBand5: (Float) -> Unit,
  val bassBoost: Float,
  val updateBassBoost: (Float) -> Unit,
  val loud: Boolean,
  val updateLoud: (Boolean) -> Unit,
  val gain: Float,
  val updateGain: (Float) -> Unit,
  val auxGain: Float,
  val updateAuxGain: (Float) -> Unit,
  val channel: Float,
  val updateChannel: (Float) -> Unit,
  val auxChannel: Float,
  val updateAuxChannel: (Float) -> Unit,
  val saveAs: () -> Unit
)

@Provide fun configModel(
  key: ConfigKey,
  configRepository: ConfigRepository,
  minirigRepository: MinirigRepository,
  navigator: Navigator
) = Model {
  val config = configRepository.config(key.id).bindResource()
  ConfigModel(
    name = if (!key.id.isMinirigAddress()) key.id
    else minirigRepository.minirig(key.id)
      .map { it?.name ?: key.id }
      .bind(key.id),
    band1 = config.map { it?.band1 }.getOrNull() ?: 0f,
    updateBand1 = action { value ->
      configRepository.updateConfig(config.get()?.copy(band1 = value) ?: return@action)
    },
    band2 = config.map { it?.band2 }.getOrNull() ?: 0f,
    updateBand2 = action { value ->
      configRepository.updateConfig(config.get()?.copy(band2 = value) ?: return@action)
    },
    band3 = config.map { it?.band3 }.getOrNull() ?: 0f,
    updateBand3 = action { value ->
      configRepository.updateConfig(config.get()?.copy(band3 = value) ?: return@action)
    },
    band4 = config.map { it?.band4 }.getOrNull() ?: 0f,
    updateBand4 = action { value ->
      configRepository.updateConfig(config.get()?.copy(band4 = value) ?: return@action)
    },
    band5 = config.map { it?.band5 }.getOrNull() ?: 0f,
    updateBand5 = action { value ->
      configRepository.updateConfig(config.get()?.copy(band5 = value) ?: return@action)
    },
    bassBoost = config.map { it?.bassBoost }.getOrNull() ?: 0f,
    updateBassBoost = action { value ->
      configRepository.updateConfig(config.get()?.copy(bassBoost = value) ?: return@action)
    },
    loud = config.map { it?.loud }.getOrNull() ?: false,
    updateLoud = action { value ->
      configRepository.updateConfig(config.get()?.copy(loud = value) ?: return@action)
    },
    gain = config.map { it?.gain }.getOrNull() ?: 0f,
    updateGain = action { value ->
      configRepository.updateConfig(config.get()?.copy(gain = value) ?: return@action)
    },
    auxGain = config.map { it?.auxGain }.getOrNull() ?: 0f,
    updateAuxGain = action { value ->
      configRepository.updateConfig(config.get()?.copy(auxGain = value) ?: return@action)
    },
    channel = config.map { it?.channel }.getOrNull() ?: 0f,
    updateChannel = action { value ->
      configRepository.updateConfig(config.get()?.copy(channel = value) ?: return@action)
    },
    auxChannel = config.map { it?.auxChannel }.getOrNull() ?: 0f,
    updateAuxChannel = action { value ->
      configRepository.updateConfig(config.get()?.copy(auxChannel = value) ?: return@action)
    },
    saveAs = action {
      val id = navigator.push(ConfigIdPickerKey()) ?: return@action
      configRepository.updateConfig(config.get()?.copy(id = id) ?: return@action)
    }
  )
}
