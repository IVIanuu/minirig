/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import com.ivianuu.essentials.app.AppForegroundState
import com.ivianuu.essentials.compose.action
import com.ivianuu.essentials.compose.bind
import com.ivianuu.essentials.compose.bindResource
import com.ivianuu.essentials.coroutines.infiniteEmptyFlow
import com.ivianuu.essentials.coroutines.parForEach
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.resource.Resource
import com.ivianuu.essentials.resource.getOrNull
import com.ivianuu.essentials.ui.common.VerticalList
import com.ivianuu.essentials.ui.common.interactive
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.material.guessingContentColorFor
import com.ivianuu.essentials.ui.material.incrementingStepPolicy
import com.ivianuu.essentials.ui.navigation.KeyUiContext
import com.ivianuu.essentials.ui.navigation.Model
import com.ivianuu.essentials.ui.navigation.ModelKeyUi
import com.ivianuu.essentials.ui.navigation.RootKey
import com.ivianuu.essentials.ui.popup.PopupMenuButton
import com.ivianuu.essentials.ui.popup.PopupMenuItem
import com.ivianuu.essentials.ui.prefs.ScaledPercentageUnitText
import com.ivianuu.essentials.ui.prefs.SliderListItem
import com.ivianuu.essentials.ui.prefs.SwitchListItem
import com.ivianuu.essentials.ui.resource.ResourceBox
import com.ivianuu.injekt.Provide
import com.ivianuu.minirig.data.Minirig
import com.ivianuu.minirig.data.MinirigConfig
import com.ivianuu.minirig.data.MinirigPrefs
import com.ivianuu.minirig.data.PowerState
import com.ivianuu.minirig.data.merge
import com.ivianuu.minirig.domain.MinirigRemote
import com.ivianuu.minirig.domain.MinirigRepository
import com.ivianuu.minirig.domain.MinirigUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Provide object HomeKey : RootKey

@Provide val homeUi = ModelKeyUi<HomeKey, HomeModel> {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Minirig") },
        actions = {
          PopupMenuButton {
            PopupMenuItem(onSelected = twsPair) {
              Text("Tws pair")
            }
            PopupMenuItem(onSelected = cancelTws) {
              Text("Cancel tws")
            }
            PopupMenuItem(onSelected = enablePowerOut) {
              Text("Enable power out")
            }
            PopupMenuItem(onSelected = powerOff) {
              Text("Power off")
            }
            PopupMenuItem(onSelected = factoryReset) {
              Text("Factory reset")
            }
          }
        }
      )
    }
  ) {
    ResourceBox(minirigs) { value ->
      VerticalList {
        if (value.isEmpty()) {
          item {
            Text("No minirigs paired")
          }
        } else {
          item {
            FlowRow(
              modifier = Modifier
                .padding(8.dp),
              mainAxisSpacing = 8.dp,
              crossAxisSpacing = 8.dp
            ) {
              val allMinirigs =
                minirigs.getOrNull()?.map { it.minirig.address }?.toSet() ?: emptySet()

              Minirig(
                selected = allMinirigs.all { it in selectedMinirigs },
                active = true,
                onClick = toggleAllMinirigSelections,
                onLongClick = null
              ) {
                Text("ALL")
              }

              value.forEach { minirig ->
                Minirig(
                  selected = minirig.minirig.address in selectedMinirigs,
                  active = minirig.isConnected,
                  onClick = { toggleMinirigSelection(minirig, false) },
                  onLongClick = { toggleMinirigSelection(minirig, true) }
                ) {
                  Text(
                    buildString {
                      append(minirig.minirig.name)
                      if (minirig.batteryPercentage != null)
                        append(", bat ${minirig.batteryPercentage}%")

                      if (minirig.powerState == PowerState.CHARGING)
                        append(", charging")
                      else if (minirig.powerState == PowerState.POWER_OUT)
                        append(", power out")
                    }
                  )
                }
              }
            }
          }

          if (selectedMinirigs.isEmpty()) {
            item {
              Text("Select a minirig to edit")
            }
          } else {
            item {
              SliderListItem(
                value = config.minirigGain,
                onValueChange = updateMinirigGain,
                title = { Text("Minirig gain") },
                stepPolicy = incrementingStepPolicy(0.1f),
                valueText = { ScaledPercentageUnitText(it) }
              )
            }

            item {
              SliderListItem(
                value = config.auxGain,
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
                value = config.bassBoost,
                onValueChange = updateBassBoost,
                valueRange = 0..7,
                title = { Text("Bass boost") },
                valueText = { Text(it.toString()) }
              )
            }

            item {
              SwitchListItem(
                value = config.loud,
                onValueChange = updateLoud,
                title = { Text("Loud") }
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Minirig(
  selected: Boolean,
  active: Boolean,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)?,
  content: @Composable () -> Unit
) {
  val backgroundColor = if (selected) MaterialTheme.colors.secondary
  else LocalContentColor.current.copy(alpha = ContentAlpha.disabled)
  Surface(
    modifier = Modifier
      .height(32.dp)
      .alpha(if (active) 1f else ContentAlpha.disabled),
    shape = RoundedCornerShape(50),
    color = backgroundColor,
    contentColor = guessingContentColorFor(backgroundColor)
  ) {
    Box(
      modifier = Modifier
        .combinedClickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = LocalIndication.current,
          onClick = onClick,
          onLongClick = onLongClick
        )
        .padding(horizontal = 8.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center
    ) {
      CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.button,
        content = content
      )
    }
  }
}

data class UiMinirig(
  val minirig: Minirig,
  val isConnected: Boolean,
  val batteryPercentage: Int?,
  val powerState: PowerState
)

data class HomeModel(
  val minirigs: Resource<List<UiMinirig>>,
  val selectedMinirigs: Set<String>,
  val toggleMinirigSelection: (UiMinirig, Boolean) -> Unit,
  val toggleAllMinirigSelections: () -> Unit,
  val twsPair: () -> Unit,
  val cancelTws: () -> Unit,
  val enablePowerOut: () -> Unit,
  val powerOff: () -> Unit,
  val factoryReset: () -> Unit,
  val config: MinirigConfig,
  val updateMinirigGain: (Float) -> Unit,
  val updateAuxGain: (Float) -> Unit,
  val updateBassBoost: (Int) -> Unit,
  val updateLoud: (Boolean) -> Unit
) {
  val bassBoostEnabled: Boolean
    get() = !config.loud
}

@Provide fun homeModel(
  appForegroundState: Flow<AppForegroundState>,
  ctx: KeyUiContext<HomeKey>,
  logger: Logger,
  pref: DataStore<MinirigPrefs>,
  remote: MinirigRemote,
  repository: MinirigRepository,
  useCases: MinirigUseCases
) = Model {
  val prefs = pref.data.bind(MinirigPrefs())

  val minirigs = appForegroundState
    .flatMapLatest { foregroundState ->
      if (foregroundState == AppForegroundState.BACKGROUND) infiniteEmptyFlow()
      else repository.minirigs
        .flatMapLatest { minirigs ->
          if (minirigs.isEmpty()) flowOf(emptyList())
          else combine(
            minirigs
              .sortedBy { it.name }
              .map { minirig ->
                remote.minirigState(minirig.address)
                  .map {
                    UiMinirig(
                      minirig = minirig,
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

  val config = prefs.selectedMinirigs
    .map { prefs.configs[it] ?: MinirigConfig() }
    .merge()

  suspend fun updateConfig(block: MinirigConfig.() -> MinirigConfig) {
    pref.updateData {
      copy(
        configs = buildMap {
          putAll(configs)
          selectedMinirigs.forEach {
            put(it, block(prefs.configs[it] ?: MinirigConfig()))
          }
        }
      )
    }
  }

  HomeModel(
    minirigs = minirigs,
    selectedMinirigs = prefs.selectedMinirigs,
    toggleMinirigSelection = action { minirig, longClick ->
      pref.updateData {
        copy(
          selectedMinirigs = if (!longClick) setOf(minirig.minirig.address)
          else selectedMinirigs.toMutableSet().apply {
            if (minirig.minirig.address in this) remove(minirig.minirig.address)
            else add(minirig.minirig.address)
          }
        )
      }
    },
    toggleAllMinirigSelections = action {
      pref.updateData {
        val allMinirigs =
          minirigs.getOrNull()?.map { it.minirig.address }?.toSet() ?: emptySet()
        copy(
          selectedMinirigs = if (allMinirigs.all { it in selectedMinirigs }) emptySet()
          else allMinirigs
        )
      }
    },
    twsPair = action {
      prefs.selectedMinirigs.parForEach { useCases.twsPair(it) }
    },
    cancelTws = action {
      prefs.selectedMinirigs.parForEach { useCases.cancelTws(it) }
    },
    enablePowerOut = action {
      prefs.selectedMinirigs.parForEach { useCases.enablePowerOut(it) }
    },
    powerOff = action {
      prefs.selectedMinirigs.parForEach { useCases.powerOff(it) }
    },
    factoryReset = action {
      prefs.selectedMinirigs.parForEach { useCases.factoryReset(it) }
    },
    config = config,
    updateMinirigGain = action { value -> updateConfig { copy(minirigGain = value) } },
    updateAuxGain = action { value -> updateConfig { copy(auxGain = value) } },
    updateBassBoost = action { value -> updateConfig { copy(bassBoost = value) } },
    updateLoud = action { value -> updateConfig { copy(loud = value) } }
  )
}
