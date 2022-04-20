/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivianuu.essentials.app.AppForegroundState
import com.ivianuu.essentials.coroutines.infiniteEmptyFlow
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.resource.Resource
import com.ivianuu.essentials.state.action
import com.ivianuu.essentials.state.bindResource
import com.ivianuu.essentials.state.state
import com.ivianuu.essentials.ui.layout.center
import com.ivianuu.essentials.ui.material.ListItem
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.KeyUiScope
import com.ivianuu.essentials.ui.navigation.Navigator
import com.ivianuu.essentials.ui.navigation.push
import com.ivianuu.essentials.ui.popup.PopupMenu
import com.ivianuu.essentials.ui.popup.PopupMenuButton
import com.ivianuu.essentials.ui.resource.ResourceVerticalListFor
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.coroutines.NamedCoroutineScope
import com.ivianuu.minirig.R
import com.ivianuu.minirig.data.MinirigConfig
import com.ivianuu.minirig.data.PowerState
import com.ivianuu.minirig.data.TwsState
import com.ivianuu.minirig.data.apply
import com.ivianuu.minirig.data.applyEq
import com.ivianuu.minirig.data.applyGain
import com.ivianuu.minirig.data.isMinirigAddress
import com.ivianuu.minirig.domain.ActiveMinirigOps
import com.ivianuu.minirig.domain.ConfigRepository
import com.ivianuu.minirig.domain.MinirigConnectionUseCases
import com.ivianuu.minirig.domain.MinirigRepository
import com.ivianuu.minirig.domain.TroubleshootingUseCases
import com.ivianuu.minirig.domain.TwsUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

fun interface MinirigsUi {
  @Composable operator fun invoke()
}

@Provide fun minirigsUi(models: StateFlow<MinirigsModel>) = MinirigsUi {
  val model by models.collectAsState()

  Scaffold(topBar = { TopAppBar(title = { Text("Minirig") }) }) {
    ResourceVerticalListFor(
      modifier = Modifier.fillMaxSize(),
      resource = model.minirigs,
      successEmpty = {
        Text(
          modifier = Modifier.center(),
          text = "No minirigs found",
          style = MaterialTheme.typography.body2
        )
      }
    ) { Minirig(it, model) }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Minirig(minirig: UiMinirig, model: MinirigsModel) {
  ListItem(
    modifier = Modifier
      .clickable { model.openMinirig(minirig) },
    leading = {
      Surface(
        modifier = Modifier.size(36.dp),
        color = animateColorAsState(
          if (minirig.isConnected) MaterialTheme.colors.secondary
          else LocalContentColor.current.copy(alpha = 0.12f)
        ).value,
        shape = CircleShape
      ) {
        if (minirig.isActive || minirig.isTwsSlave)
          Icon(
            modifier = Modifier.center(),
            painterResId = if (minirig.isActive)
              R.drawable.ic_volume_up
            else R.drawable.es_ic_done
          )
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
                  PowerState.POWER_OUT -> "Power out"
                }
              }"
            }.orEmpty()
      )
    },
    trailing = {
      PopupMenuButton(
        items = listOf(
          PopupMenu.Item(onSelected = { model.applyConfig(minirig) }) {
            Text("Apply config")
          },
          PopupMenu.Item(onSelected = { model.applyEq(minirig) }) {
            Text("Apply equalizer")
          },
          PopupMenu.Item(onSelected = { model.applyGain(minirig) }) {
            Text("Apply gain")
          },
          PopupMenu.Item(onSelected = { model.makeActive(minirig) }) {
            Text("Set active")
          },
          PopupMenu.Item(onSelected = { model.connect(minirig) }) {
            Text("Connect")
          },
          PopupMenu.Item(onSelected = { model.disconnect(minirig) }) {
            Text("Disconnect")
          },
          PopupMenu.Item(onSelected = { model.twsPair(minirig) }) {
            Text("Tws pair")
          },
          PopupMenu.Item(onSelected = { model.cancelTws(minirig) }) {
            Text("Cancel tws")
          },
          PopupMenu.Item(onSelected = { model.enablePowerOut(minirig) }) {
            Text("Enable power out")
          },
          PopupMenu.Item(onSelected = { model.powerOff(minirig) }) {
            Text("Power off")
          },
          PopupMenu.Item(onSelected = { model.debug(minirig) }) {
            Text("Debug")
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
  val isActive: Boolean,
  val isTwsSlave: Boolean,
  val batteryPercentage: Int?,
  val powerState: PowerState
)

data class MinirigsModel(
  val minirigs: Resource<List<UiMinirig>>,
  val openMinirig: (UiMinirig) -> Unit,
  val applyConfig: (UiMinirig) -> Unit,
  val applyEq: (UiMinirig) -> Unit,
  val applyGain: (UiMinirig) -> Unit,
  val connect: (UiMinirig) -> Unit,
  val disconnect: (UiMinirig) -> Unit,
  val makeActive: (UiMinirig) -> Unit,
  val twsPair: (UiMinirig) -> Unit,
  val cancelTws: (UiMinirig) -> Unit,
  val enablePowerOut: (UiMinirig) -> Unit,
  val powerOff: (UiMinirig) -> Unit,
  val debug: (UiMinirig) -> Unit,
  val factoryReset: (UiMinirig) -> Unit
)

@Provide fun minirigsModel(
  activeMinirigOps: ActiveMinirigOps,
  appForegroundState: Flow<AppForegroundState>,
  configRepository: ConfigRepository,
  connectionUseCases: MinirigConnectionUseCases,
  twsUseCases: TwsUseCases,
  minirigRepository: MinirigRepository,
  navigator: Navigator,
  scope: NamedCoroutineScope<KeyUiScope>,
  troubleshootingUseCases: TroubleshootingUseCases,
  L: Logger
) = scope.state {
  suspend fun apply(
    address: String,
    transform: MinirigConfig.(MinirigConfig) -> MinirigConfig
  ) {
    val configs = configRepository.configs
      .first()
      .filter { !it.id.isMinirigAddress() }

    val pickedConfig = navigator.push(ConfigPickerKey(configs)) ?: return

    val minirigConfig = configRepository.config(address).first()!!
    configRepository.updateConfig(minirigConfig.transform(pickedConfig))
  }

  suspend fun applyConfig(address: String) = apply(address) { apply(it) }

  suspend fun applyEq(address: String) = apply(address) { applyEq(it) }

  suspend fun applyGain(address: String) = apply(address) { applyGain(it) }

  val minirigs = appForegroundState
    .flatMapLatest { foregroundState ->
      if (foregroundState == AppForegroundState.BACKGROUND) infiniteEmptyFlow()
      else combine(
        minirigRepository.minirigs,
        activeMinirigOps.activeMinirig.onStart { emit(null) }
      ) { a, b -> a to b }
        .flatMapLatest { (minirigs, activeMinirig) ->
          if (minirigs.isEmpty()) flowOf(emptyList())
          else combine(
            minirigs
              .sortedBy { it.name }
              .map { minirig ->
                minirigRepository.minirigState(minirig.address)
                  .map {
                    UiMinirig(
                      minirig.address,
                      minirig.name,
                      it.isConnected,
                      minirig.address == activeMinirig,
                      it.twsState == TwsState.SLAVE,
                      (it.batteryPercentage?.let { it * 100 })?.toInt(),
                      it.powerState
                    )
                  }
              }
          ) { it.toList() }
        }
    }
    .bindResource()

  MinirigsModel(
    minirigs = minirigs,
    openMinirig = action { minirig -> navigator.push(ConfigKey(minirig.address)) },
    applyConfig = action { minirig -> applyConfig(minirig.address) },
    applyEq = action { minirig -> applyEq(minirig.address) },
    applyGain = action { minirig -> applyGain(minirig.address) },
    connect = action { minirig -> connectionUseCases.connectMinirig(minirig.address) },
    disconnect = action { minirig -> connectionUseCases.disconnectMinirig(minirig.address) },
    makeActive = action { minirig -> activeMinirigOps.setActiveMinirig(minirig.address) },
    cancelTws = action { minirig -> twsUseCases.cancelTws(minirig.address) },
    twsPair = action { minirig -> twsUseCases.twsPair(minirig.address) },
    enablePowerOut = action { minirig -> troubleshootingUseCases.enablePowerOut(minirig.address) },
    powerOff = action { minirig -> troubleshootingUseCases.powerOff(minirig.address) },
    debug = action { minirig -> navigator.push(MinirigDebugKey(minirig.address)) },
    factoryReset = action { minirig -> troubleshootingUseCases.factoryReset(minirig.address) }
  )
}
