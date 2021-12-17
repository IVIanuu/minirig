/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import android.bluetooth.*
import android.media.*
import android.media.session.*
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.app.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.resource.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.animation.*
import com.ivianuu.essentials.ui.backpress.*
import com.ivianuu.essentials.ui.common.*
import com.ivianuu.essentials.ui.layout.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.essentials.ui.popup.*
import com.ivianuu.essentials.ui.resource.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.R
import com.ivianuu.minirig.data.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun interface MinirigsUi : @Composable () -> Unit

@Provide fun minirigsUi(models: StateFlow<MinirigsModel>) = MinirigsUi {
  val model by models.collectAsState()

  if (model.isSelectionMode)
    BackHandler(model.deselectAll)

  Scaffold(
    topBar = {
      AnimatedBox(model.isSelectionMode) { currentSelectionMode ->
        if (currentSelectionMode) {
          TopAppBar(
            leading = {
              IconButton(onClick = model.deselectAll) {
                Icon(Icons.Default.Clear)
              }
            },
            title = { Spacer(Modifier.fillMaxWidth()) },
            actions = {
              IconButton(onClick = model.selectAll) {
                Icon(R.drawable.ic_select_all)
              }

              PopupMenuButton(
                items = listOf(
                  PopupMenu.Item(onSelected = model.applyConfigToSelected) {
                    Text("Apply config")
                  },
                  PopupMenu.Item(onSelected = model.applyEqToSelected) {
                    Text("Apply equalizer")
                  },
                  PopupMenu.Item(onSelected = model.applyGainToSelected) {
                    Text("Apply gain")
                  },
                  PopupMenu.Item(onSelected = model.connectSelected) {
                    Text("Connect")
                  },
                  PopupMenu.Item(onSelected = model.disconnectSelected) {
                    Text("Disconnect")
                  },
                  PopupMenu.Item(onSelected = model.startLinkupWithSelected) {
                    Text("Start linkup")
                  },
                  PopupMenu.Item(onSelected = model.joinLinkupSelected) {
                    Text("Join linkup")
                  },
                  PopupMenu.Item(onSelected = model.cancelLinkupForSelected) {
                    Text("Cancel linkup")
                  },
                  PopupMenu.Item(onSelected = model.powerOffSelected) {
                    Text("Power off")
                  },
                )
              )
            }
          )
        } else {
          TopAppBar(title = { Text("Minirig") })
        }
      }
    }
  ) {
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

@Composable private fun Minirig(minirig: UiMinirig, model: MinirigsModel) {
  ListItem(
    modifier = Modifier
      .combinedClickable(
        onClick = {
          if (model.isSelectionMode) model.toggleSelectMinirig(minirig)
          else model.openMinirig(minirig)
        },
        onLongClick = { model.toggleSelectMinirig(minirig) }
      )
      .background(
        if (minirig.address in model.selectedMinirigs) LocalContentColor.current.copy(alpha = 0.12f)
        else Color.Transparent
      ),
    leading = {
      Surface(
        modifier = Modifier.size(36.dp),
        color = animateColorAsState(
          if (minirig.isConnected) MaterialTheme.colors.secondary
          else LocalContentColor.current.copy(alpha = 0.12f)
        ).value,
        shape = CircleShape
      ) {
        if (minirig.isActive || minirig.isLinkupSlave)
          Icon(
            modifier = Modifier.center(),
            painterResId = if (minirig.isActive)
              R.drawable.ic_volume_up
            else R.drawable.es_ic_done
          )
      }
    },
    title = { Text(minirig.name) },
    subtitle = { Text(minirig.address) },
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
          PopupMenu.Item(onSelected = { model.rename(minirig) }) {
            Text("Rename")
          },
          PopupMenu.Item(onSelected = { model.clearPairedDevices(minirig) }) {
            Text("Clear paired devices")
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
  val isLinkupSlave: Boolean
)

data class MinirigsModel(
  val minirigs: Resource<List<UiMinirig>>,
  val selectedMinirigs: Set<String>,
  val selectAll: () -> Unit,
  val deselectAll: () -> Unit,
  val openMinirig: (UiMinirig) -> Unit,
  val toggleSelectMinirig: (UiMinirig) -> Unit,
  val applyConfig: (UiMinirig) -> Unit,
  val applyEq: (UiMinirig) -> Unit,
  val applyGain: (UiMinirig) -> Unit,
  val applyConfigToSelected: () -> Unit,
  val applyEqToSelected: () -> Unit,
  val applyGainToSelected: () -> Unit,
  val connect: (UiMinirig) -> Unit,
  val connectSelected: () -> Unit,
  val disconnect: (UiMinirig) -> Unit,
  val disconnectSelected: () -> Unit,
  val makeActive: (UiMinirig) -> Unit,
  val startLinkup: (UiMinirig) -> Unit,
  val startLinkupWithSelected: () -> Unit,
  val joinLinkup: (UiMinirig) -> Unit,
  val joinLinkupSelected: () -> Unit,
  val cancelLinkup: (UiMinirig) -> Unit,
  val cancelLinkupForSelected: () -> Unit,
  val powerOff: (UiMinirig) -> Unit,
  val powerOffSelected: () -> Unit,
  val rename: (UiMinirig) -> Unit,
  val clearPairedDevices: (UiMinirig) -> Unit,
  val factoryReset: (UiMinirig) -> Unit
) {
  val isSelectionMode: Boolean
    get() = selectedMinirigs.isNotEmpty()
}

@Provide fun minirigsModel(
  activeMinirigOps: ActiveMinirigOps,
  appForegroundState: Flow<AppForegroundState>,
  configRepository: ConfigRepository,
  connectionUseCases: MinirigConnectionUseCases,
  linkupUseCases: LinkupUseCases,
  minirigRepository: MinirigRepository,
  navigator: Navigator,
  troubleshootingUseCases: TroubleshootingUseCases,
  L: Logger,
  S: NamedCoroutineScope<KeyUiScope>
) = state {
  suspend fun apply(
    addresses: Collection<String>,
    transform: MinirigConfig.(MinirigConfig) -> MinirigConfig
  ) {
    val configs = configRepository.configs
      .first()
      .filter { !it.id.isMinirigAddress() }

    val pickedConfig = navigator.push(ConfigPickerKey(configs)) ?: return

    for (address in addresses) {
      val minirigConfig = configRepository.config(address).first()!!
      configRepository.updateConfig(minirigConfig.transform(pickedConfig))
    }
  }

  suspend fun applyConfig(addresses: Collection<String>) = apply(addresses) { apply(it) }

  suspend fun applyEq(addresses: Collection<String>) = apply(addresses) { applyEq(it) }

  suspend fun applyGain(addresses: Collection<String>) = apply(addresses) { applyGain(it) }

  val minirigs = appForegroundState
    .flatMapLatest {
      if (it == AppForegroundState.BACKGROUND) infiniteEmptyFlow()
      else combine(
        minirigRepository.minirigs,
        activeMinirigOps.activeMinirig.onStart { emit(null) }
      )
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
                      it.linkupState == LinkupState.SLAVE
                    )
                  }
              }
          ) { it.toList() }
        }
    }
    .bindResource()

  var selectedMinirigs by memo { stateVar(emptySet<String>()) }

  MinirigsModel(
    minirigs = minirigs,
    selectedMinirigs = selectedMinirigs,
    selectAll = {
      minirigs.getOrNull()?.forEach { selectedMinirigs = selectedMinirigs + it.address }
    },
    deselectAll = { selectedMinirigs = emptySet() },
    toggleSelectMinirig = {
      selectedMinirigs = if (it.address !in selectedMinirigs) selectedMinirigs + it.address
      else selectedMinirigs - it.address
    },
    openMinirig = action { minirig -> navigator.push(ConfigKey(minirig.address)) },
    applyConfig = action { minirig -> applyConfig(listOf(minirig.address)) },
    applyConfigToSelected = action { applyConfig(selectedMinirigs) },
    applyEq = action { minirig -> applyEq(listOf(minirig.address)) },
    applyEqToSelected = action { applyEq(selectedMinirigs) },
    applyGain = action { minirig -> applyGain(listOf(minirig.address)) },
    applyGainToSelected = action { applyGain(selectedMinirigs) },
    connect = action { minirig -> connectionUseCases.connectMinirig(minirig.address) },
    connectSelected = action { selectedMinirigs.forEach { connectionUseCases.connectMinirig(it) } },
    disconnect = action { minirig -> connectionUseCases.disconnectMinirig(minirig.address) },
    disconnectSelected = action { selectedMinirigs.forEach { connectionUseCases.disconnectMinirig(it) } },
    makeActive = action { minirig -> activeMinirigOps.setActiveMinirig(minirig.address) },
    startLinkup = action { minirig -> linkupUseCases.startLinkup(minirig.address) },
    joinLinkup = action { minirig -> linkupUseCases.joinLinkup(minirig.address) },
    joinLinkupSelected = action {
      selectedMinirigs.forEach { linkupUseCases.joinLinkup(it) }
    },
    cancelLinkup = action { minirig -> linkupUseCases.cancelLinkup(minirig.address) },
    cancelLinkupForSelected = action {
      selectedMinirigs.forEach { linkupUseCases.cancelLinkup(it) }
    },
    startLinkupWithSelected = action {
      val pickerMinirigs = selectedMinirigs
        .parMap { minirigRepository.minirig(it).first()!! }

      val host = navigator.push(MinirigPickerKey(pickerMinirigs))
        ?: return@action

      linkupUseCases.startLinkup(host.address, selectedMinirigs.filterNot { it == host.address })
    },
    powerOff = action { minirig -> troubleshootingUseCases.powerOff(minirig.address) },
    powerOffSelected = action {
      selectedMinirigs.forEach { troubleshootingUseCases.powerOff(it) }
    },
    rename = action { minirig ->
      val newName = navigator.push(RenameMinirigKey()) ?: return@action
      troubleshootingUseCases.rename(minirig.address, newName)
    },
    clearPairedDevices = action { minirig ->
      troubleshootingUseCases.clearPairedDevices(minirig.address)
    },
    factoryReset = action { minirig -> troubleshootingUseCases.factoryReset(minirig.address) }
  )
}
