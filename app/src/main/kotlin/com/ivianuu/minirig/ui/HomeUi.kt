/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivianuu.essentials.app.AppForegroundState
import com.ivianuu.essentials.coroutines.infiniteEmptyFlow
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.resource.Resource
import com.ivianuu.essentials.resource.getOrNull
import com.ivianuu.essentials.state.action
import com.ivianuu.essentials.state.bind
import com.ivianuu.essentials.state.bindResource
import com.ivianuu.essentials.ui.common.VerticalList
import com.ivianuu.essentials.ui.common.interactive
import com.ivianuu.essentials.ui.material.ListItem
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.Subheader
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.material.incrementingStepPolicy
import com.ivianuu.essentials.ui.navigation.Model
import com.ivianuu.essentials.ui.navigation.ModelKeyUi
import com.ivianuu.essentials.ui.navigation.Navigator
import com.ivianuu.essentials.ui.navigation.RootKey
import com.ivianuu.essentials.ui.navigation.push
import com.ivianuu.essentials.ui.popup.PopupMenu
import com.ivianuu.essentials.ui.popup.PopupMenuButton
import com.ivianuu.essentials.ui.prefs.ScaledPercentageUnitText
import com.ivianuu.essentials.ui.prefs.SliderListItem
import com.ivianuu.essentials.ui.prefs.SwitchListItem
import com.ivianuu.injekt.Provide
import com.ivianuu.minirig.data.MinirigPrefs
import com.ivianuu.minirig.data.PowerState
import com.ivianuu.minirig.domain.LinkupUseCases
import com.ivianuu.minirig.domain.MinirigRepository
import com.ivianuu.minirig.domain.TroubleshootingUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Provide object HomeKey : RootKey

@Provide val homeUi = ModelKeyUi<HomeKey, HomeModel> {
  Scaffold(topBar = { TopAppBar(title = { Text("Minirig") }) }) {
    val minirigs = minirigs.getOrNull() ?: emptyList()

    VerticalList {
      item {
        Subheader { Text("Minirigs") }
      }

      if (minirigs.isEmpty()) {
        item {
          Text(
            modifier = Modifier.padding(16.dp),
            text = "No minirigs found"
          )
        }
      } else {
        // todo remove address in a future compose version
        items(minirigs, key = { it.address }) {
          Minirig(it, this@ModelKeyUi)
        }
      }

      item {
        Subheader { Text("Settings") }
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
        SliderListItem(
          value = minirigGain,
          onValueChange = updateMinirigGain,
          title = { Text("Minirig gain") },
          stepPolicy = incrementingStepPolicy(0.1f),
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
        SliderListItem(
          modifier = Modifier
            .interactive(bassBoostEnabled),
          value = bassBoost,
          onValueChange = updateBassBoost,
          valueRange = 0..7,
          title = { Text("Bass boost") },
          valueText = { Text(it.toString()) }
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
        SwitchListItem(
          value = mono,
          onValueChange = updateMono,
          title = { Text("Mono") }
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Minirig(minirig: UiMinirig, model: HomeModel) {
  ListItem(
    leading = {
      Surface(
        modifier = Modifier.size(36.dp),
        color = animateColorAsState(
          if (minirig.isConnected) MaterialTheme.colors.secondary
          else LocalContentColor.current.copy(alpha = 0.12f)
        ).value,
        shape = CircleShape
      ) {
      }
    },
    title = { Text(minirig.name) },
    subtitle = {
      Text(
        if (!minirig.isConnected) "Not connected"
        else (minirig.batteryPercentage?.let { "Battery $it%" } ?: "Unknown battery") +
            minirig.powerState.takeIf { it != PowerState.NORMAL }?.let { powerState ->
              " • ${
                when (powerState) {
                  PowerState.NORMAL -> throw AssertionError()
                  PowerState.CHARGING -> "Charging"
                }
              }"
            }.orEmpty()
      )
    },
    trailing = {
      PopupMenuButton(
        items = listOf(
          PopupMenu.Item(onSelected = { model.channelSettings(minirig) }) {
            Text("Channel settings")
          },
          PopupMenu.Item(onSelected = { model.twsPair(minirig) }) {
            Text("Tws pair")
          },
          PopupMenu.Item(onSelected = { model.startLinkup(minirig) }) {
            Text("Start linkup")
          },
          PopupMenu.Item(onSelected = { model.joinLinkup(minirig) }) {
            Text("Join linkup")
          },
          PopupMenu.Item(onSelected = { model.cancelLinkup(minirig) }) {
            Text("Cancel linkup")
          },
          PopupMenu.Item(onSelected = { model.powerOff(minirig) }) {
            Text("Power off")
          },
          PopupMenu.Item(onSelected = { model.factoryReset(minirig) }) {
            Text("Factory reset")
          }
        )
      )
    }
  )
}

data class UiMinirig(
  val address: String,
  val name: String,
  val isConnected: Boolean,
  val batteryPercentage: Int?,
  val powerState: PowerState
)

data class HomeModel(
  val minirigs: Resource<List<UiMinirig>>,
  val twsPair: (UiMinirig) -> Unit,
  val startLinkup: (UiMinirig) -> Unit,
  val joinLinkup: (UiMinirig) -> Unit,
  val cancelLinkup: (UiMinirig) -> Unit,
  val powerOff: (UiMinirig) -> Unit,
  val factoryReset: (UiMinirig) -> Unit,
  val channelSettings: (UiMinirig) -> Unit,
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
  val minirigGain: Float,
  val updateMinirigGain: (Float) -> Unit,
  val auxGain: Float,
  val updateAuxGain: (Float) -> Unit,
  val bassBoost: Int,
  val updateBassBoost: (Int) -> Unit,
  val loud: Boolean,
  val updateLoud: (Boolean) -> Unit,
  val mono: Boolean,
  val updateMono: (Boolean) -> Unit
) {
  val bassBoostEnabled: Boolean
    get() = !loud
}

@Provide fun homeModel(
  appForegroundState: Flow<AppForegroundState>,
  minirigRepository: MinirigRepository,
  navigator: Navigator,
  pref: DataStore<MinirigPrefs>,
  troubleshootingUseCases: TroubleshootingUseCases,
  linkupUseCases: LinkupUseCases,
  logger: Logger
) = Model {
  val minirigs = appForegroundState
    .flatMapLatest { foregroundState ->
      if (foregroundState == AppForegroundState.BACKGROUND) infiniteEmptyFlow()
      else minirigRepository.minirigs
        .flatMapLatest { minirigs ->
          if (minirigs.isEmpty()) flowOf(emptyList())
          else combine(
            minirigs
              .sortedBy { it.name }
              .map { minirig ->
                minirigRepository.minirigState(minirig.address)
                  .map {
                    UiMinirig(
                      address = minirig.address,
                      name = minirig.name,
                      isConnected = it.isConnected,
                      batteryPercentage = (it.batteryPercentage?.let { it * 100 })?.toInt(),
                      powerState = it.powerState
                    )
                  }
              }
          ) { it.toList() }
        }
    }
    .bindResource()

  val prefs = pref.data.bind(MinirigPrefs())

  HomeModel(
    minirigs = minirigs,
    twsPair = action { minirig -> linkupUseCases.twsPair(minirig.address) },
    startLinkup = action { minirig -> linkupUseCases.startLinkup(minirig.address) },
    joinLinkup = action { minirig -> linkupUseCases.joinLinkup(minirig.address) },
    cancelLinkup = action { minirig -> linkupUseCases.cancelLinkup(minirig.address) },
    powerOff = action { minirig -> troubleshootingUseCases.powerOff(minirig.address) },
    factoryReset = action { minirig -> troubleshootingUseCases.factoryReset(minirig.address) },
    channelSettings = action { minirig -> navigator.push(ChannelKey(minirig.address)) },
    band1 = prefs.band1,
    updateBand1 = action { value -> pref.updateData { copy(band1 = value) } },
    band2 = prefs.band2,
    updateBand2 = action { value -> pref.updateData { copy(band2 = value) } },
    band3 = prefs.band3,
    updateBand3 = action { value -> pref.updateData { copy(band3 = value) } },
    band4 = prefs.band4,
    updateBand4 = action { value -> pref.updateData { copy(band4 = value) } },
    band5 = prefs.band5,
    updateBand5 = action { value -> pref.updateData { copy(band5 = value) } },
    minirigGain = prefs.minirigGain,
    updateMinirigGain = action { value -> pref.updateData { copy(minirigGain = value) } },
    auxGain = prefs.auxGain,
    updateAuxGain = action { value -> pref.updateData { copy(auxGain = value) } },
    bassBoost = prefs.bassBoost,
    updateBassBoost = action { value -> pref.updateData { copy(bassBoost = value) } },
    loud = prefs.loud,
    updateLoud = action { value -> pref.updateData { copy(loud = value) } },
    mono = prefs.mono,
    updateMono = action { value -> pref.updateData { copy(mono = value) } }
  )
}
