/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FabPosition
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.ivianuu.minirig.data.MinirigConfig
import com.ivianuu.minirig.data.isMinirigAddress
import com.ivianuu.minirig.domain.ConfigRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

fun interface ConfigsUi {
  @Composable operator fun invoke()
}

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
  scope: NamedCoroutineScope<KeyUiScope>
) = scope.state {
  ConfigsModel(
    configs = repository.configs
      .map { configs ->
        configs
          .filter { !it.id.isMinirigAddress() }
          .sortedBy { it.id }
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
