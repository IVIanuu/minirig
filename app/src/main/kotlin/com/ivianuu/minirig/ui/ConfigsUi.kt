/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.ivianuu.essentials.resource.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.dialog.*
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

fun interface ConfigsUi : @Composable () -> Unit

@Provide fun configsUi(models: StateFlow<ConfigsModel>) = ConfigsUi {
  val model by models.collectAsState()

  Scaffold(
    topBar = { TopAppBar(title = { Text("Minirig") }) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = model.createConfig,
        text = { Text("CREATE CONFIG") }
      )
    },
    floatingActionButtonPosition = FabPosition.Center
  ) {
    ResourceVerticalListFor(
      modifier = Modifier.fillMaxSize(),
      resource = model.configs,
      successEmpty = {
        Text(
          modifier = Modifier.center(),
          text = "No configs created yet",
          style = MaterialTheme.typography.body2
        )
      }
    ) { config ->
      ListItem(
        modifier = Modifier.clickable { model.openConfig(config) },
        title = { Text(config.id) },
        trailing = {
          PopupMenuButton(
            items = listOf(
              PopupMenu.Item(onSelected = { model.deleteConfig(config) }) {
                Text("Delete")
              }
            )
          )
        }
      )
    }
  }
}

data class ConfigsModel(
  val configs: Resource<List<MinirigConfig>>,
  val openConfig: (MinirigConfig) -> Unit,
  val deleteConfig: (MinirigConfig) -> Unit,
  val createConfig: () -> Unit
)

@Provide fun configsModel(
  navigator: Navigator,
  repository: ConfigRepository,
  S: NamedCoroutineScope<KeyUiScope>
) = state {
  ConfigsModel(
    configs = repository.configs
      .map { configs ->
        configs
          .filter { !it.id.isMinirigAddress() }
      }
      .bindResource(),
    openConfig = action { config ->
      navigator.push(ConfigKey(config.id))
    },
    createConfig = action {
      val id = navigator.push(ConfigIdPickerKey()) ?: return@action
      repository.updateConfig(MinirigConfig(id = id))
      navigator.push(ConfigKey(id))
    },
    deleteConfig = action { config ->
      repository.deleteConfig(config.id)
    }
  )
}
