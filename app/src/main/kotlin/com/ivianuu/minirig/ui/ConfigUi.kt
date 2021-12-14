/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.material.*
import com.ivianuu.essentials.resource.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.common.*
import com.ivianuu.essentials.ui.dialog.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.essentials.ui.popup.*
import com.ivianuu.essentials.ui.prefs.*
import com.ivianuu.injekt.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.flow.*

data class ConfigKey(val id: String) : Key<Unit>

@Provide val configUi = ModelKeyUi<ConfigKey, ConfigModel> {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(model.name) },
        actions = {
          PopupMenuButton(
            items = listOf(
              PopupMenu.Item(onSelected = model.saveAs) { Text("Save as") }
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
        FloatSliderListItem(
          value = model.band1,
          onValueChange = model.updateBand1,
          title = { Text("70Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.band2,
          onValueChange = model.updateBand2,
          title = { Text("250Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.band3,
          onValueChange = model.updateBand3,
          title = { Text("850Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.band4,
          onValueChange = model.updateBand4,
          title = { Text("3KHz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.band5,
          onValueChange = model.updateBand5,
          title = { Text("10.5KHz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        Subheader { Text("Gain") }
      }

      item {
        FloatSliderListItem(
          value = model.gain,
          onValueChange = model.updateGain,
          title = { Text("Gain") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.auxGain,
          onValueChange = model.updateAuxGain,
          title = { Text("Aux gain") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.bassGain,
          onValueChange = model.updateBassGain,
          title = { Text("Bass gain") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.channel,
          onValueChange = model.updateChannel,
          title = { Text("Channel") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
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
  val gain: Float,
  val updateGain: (Float) -> Unit,
  val auxGain: Float,
  val updateAuxGain: (Float) -> Unit,
  val bassGain: Float,
  val updateBassGain: (Float) -> Unit,
  val channel: Float,
  val updateChannel: (Float) -> Unit,
  val saveAs: () -> Unit
)

@Provide fun configModel(
  key: ConfigKey,
  navigator: Navigator,
  repository: MinirigRepository,
  SS: StateScope
): ConfigModel {
  val config = repository.config(key.id).bindResource()
  return ConfigModel(
    name = if (!key.id.isMinirigAddress()) key.id
    else repository.minirig(key.id)
      .map { it?.name ?: key.id }
      .bind(key.id),
    band1 = config.map { it?.band1 }.getOrNull() ?: 0f,
    updateBand1 = action { value ->
      repository.updateConfig(config.get()!!.copy(band1 = value))
    },
    band2 = config.map { it?.band2 }.getOrNull() ?: 0f,
    updateBand2 = action { value ->
      repository.updateConfig(config.get()!!.copy(band2 = value))
    },
    band3 = config.map { it?.band3 }.getOrNull() ?: 0f,
    updateBand3 = action { value ->
      repository.updateConfig(config.get()!!.copy(band3 = value))
    },
    band4 = config.map { it?.band4 }.getOrNull() ?: 0f,
    updateBand4 = action { value ->
      repository.updateConfig(config.get()!!.copy(band4 = value))
    },
    band5 = config.map { it?.band5 }.getOrNull() ?: 0f,
    updateBand5 = action { value ->
      repository.updateConfig(config.get()!!.copy(band5 = value))
    },
    gain = config.map { it?.gain }.getOrNull() ?: 0f,
    updateGain = action { value ->
      repository.updateConfig(config.get()!!.copy(gain = value))
    },
    auxGain = config.map { it?.auxGain }.getOrNull() ?: 0f,
    updateAuxGain = action { value ->
      repository.updateConfig(config.get()!!.copy(auxGain = value))
    },
    bassGain = config.map { it?.bassGain }.getOrNull() ?: 0f,
    updateBassGain = action { value ->
      repository.updateConfig(config.get()!!.copy(bassGain = value))
    },
    channel = config.map { it?.channel }.getOrNull() ?: 0f,
    updateChannel = action { value ->
      repository.updateConfig(config.get()!!.copy(channel = value))
    },
    saveAs = action {
      val id = navigator.push(ConfigIdPickerKey()) ?: return@action
      repository.updateConfig(config.get()!!.copy(id = id))
    }
  )
}
