package com.ivianuu.minirig.ui

import androidx.compose.material.*
import com.ivianuu.essentials.resource.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.common.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.essentials.ui.prefs.*
import com.ivianuu.injekt.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.flow.*

data class MinirigKey(val address: String) : Key<Unit>

@Provide
val minirigUi = ModelKeyUi<MinirigKey, MinirigModel> {
  Scaffold(
    topBar = { TopAppBar(title = { Text(model.name) }) }
  ) {
    VerticalList {
      item {
        FloatSliderListItem(
          value = model.band1,
          onValueChange = model.updateBand1,
          title = { Text("Band 1") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.band2,
          onValueChange = model.updateBand2,
          title = { Text("70Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.band3,
          onValueChange = model.updateBand3,
          title = { Text("250Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.band4,
          onValueChange = model.updateBand4,
          title = { Text("850Hz") },
          stepPolicy = incrementingStepPolicy(0.05f),
          valueText = { ScaledPercentageUnitText(it) }
        )
      }

      item {
        FloatSliderListItem(
          value = model.band5,
          onValueChange = model.updateBand5,
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

data class MinirigModel(
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
  val updateChannel: (Float) -> Unit
)

@Provide
fun minirigModel(
  key: MinirigKey,
  minirigRepository: MinirigRepository,
  SS: StateScope
): MinirigModel {
  val config = minirigRepository.config(key.address).bindResource()
  return MinirigModel(
    name = minirigRepository.minirig(key.address)
      .map { it?.name ?: key.address }
      .bind(key.address),
    band1 = config.map { it?.band1 }.getOrNull() ?: 0f,
    updateBand1 = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(band1 = value))
    },
    band2 = config.map { it?.band2 }.getOrNull() ?: 0f,
    updateBand2 = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(band2 = value))
    },
    band3 = config.map { it?.band3 }.getOrNull() ?: 0f,
    updateBand3 = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(band3 = value))
    },
    band4 = config.map { it?.band4 }.getOrNull() ?: 0f,
    updateBand4 = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(band4 = value))
    },
    band5 = config.map { it?.band5 }.getOrNull() ?: 0f,
    updateBand5 = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(band5 = value))
    },
    gain = config.map { it?.gain }.getOrNull() ?: 0f,
    updateGain = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(gain = value))
    },
    auxGain = config.map { it?.auxGain }.getOrNull() ?: 0f,
    updateAuxGain = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(auxGain = value))
    },
    bassGain = config.map { it?.bassGain }.getOrNull() ?: 0f,
    updateBassGain = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(bassGain = value))
    },
    channel = config.map { it?.channel }.getOrNull() ?: 0f,
    updateChannel = action { value ->
      minirigRepository.updateConfig(config.get()!!.copy(channel = value))
    },
  )
}
