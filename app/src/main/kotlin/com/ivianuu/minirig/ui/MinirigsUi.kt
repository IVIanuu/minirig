/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import com.ivianuu.essentials.resource.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.layout.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.essentials.ui.popup.*
import com.ivianuu.essentials.ui.resource.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.flow.*

fun interface MinirigsUi : @Composable () -> Unit

@Provide fun minirigsUi(models: StateFlow<MinirigsModel>) = MinirigsUi {
  val model by models.collectAsState()
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Minirig") },
        actions = {
          PopupMenuButton(
            items = listOf(
              PopupMenu.Item(onSelected = model.applyConfigToAll) {
                Text("Apply config to all")
              },
              PopupMenu.Item(onSelected = model.applyEqToAll) {
                Text("Apply equalizer to all")
              },
              PopupMenu.Item(onSelected = model.applyGainToAll) {
                Text("Apply gain to all")
              }
            )
          )
        }
      )
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
    ) { minirig ->
      ListItem(
        modifier = Modifier.clickable { model.openMinirig(minirig) },
        title = { Text(minirig.name) },
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
              }
            )
          )
        }
      )
    }
  }
}

data class MinirigsModel(
  val minirigs: Resource<List<Minirig>>,
  val openMinirig: (Minirig) -> Unit,
  val applyConfig: (Minirig) -> Unit,
  val applyEq: (Minirig) -> Unit,
  val applyGain: (Minirig) -> Unit,
  val applyConfigToAll: () -> Unit,
  val applyEqToAll: () -> Unit,
  val applyGainToAll: () -> Unit
)

@Provide fun minirigsModel(
  navigator: Navigator,
  repository: MinirigRepository,
  S: NamedCoroutineScope<KeyUiScope>
) = state {
  suspend fun apply(id: String, transform: MinirigConfig.() -> MinirigConfig) {
    val minirigConfig = repository.config(id).first()!!
    repository.updateConfig(minirigConfig.transform())
  }

  suspend fun apply(id: String, other: MinirigConfig) {
    apply(id) { apply(other) }
  }

  suspend fun applyEq(id: String, other: MinirigConfig) {
    apply(id) { applyEq(other) }
  }

  suspend fun applyGain(id: String, other: MinirigConfig) {
    apply(id) { applyGain(other) }
  }

  MinirigsModel(
    minirigs = repository.minirigs.bindResource(),
    openMinirig = action { minirig -> navigator.push(ConfigKey(minirig.address)) },
    applyConfig = action { minirig ->
      val config = navigator.push(ConfigPickerKey) ?: return@action
      apply(minirig.address, config)
    },
    applyConfigToAll = action {
      val config = navigator.push(ConfigPickerKey) ?: return@action
      repository.minirigs.first().forEach { minirig ->
        apply(minirig.address, config)
      }
    },
    applyEq = action { minirig ->
      val eqConfig = navigator.push(ConfigPickerKey) ?: return@action
      applyEq(minirig.address, eqConfig)
    },
    applyEqToAll = action {
      val eqConfig = navigator.push(ConfigPickerKey) ?: return@action
      repository.minirigs.first().forEach { minirig ->
        applyEq(minirig.address, eqConfig)
      }
    },
    applyGain = action { minirig ->
      val gainConfig = navigator.push(ConfigPickerKey) ?: return@action
      applyGain(minirig.address, gainConfig)
    },
    applyGainToAll = action {
      val gainConfig = navigator.push(ConfigPickerKey) ?: return@action
      repository.minirigs.first().forEach { minirig ->
        applyGain(minirig.address, gainConfig)
      }
    }
  )
}
