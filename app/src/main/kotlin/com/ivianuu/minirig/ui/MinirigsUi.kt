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
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.essentials.ui.popup.*
import com.ivianuu.essentials.ui.resource.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.flow.*

fun interface MinirigsUi : @Composable () -> Unit

@Provide fun minirigsUi(models: StateFlow<MinirigsModel>) = MinirigsUi {
  val model by models.collectAsState()
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
              Text("Apply from")
            }
          )
        )
      }
    )
  }
}

data class MinirigsModel(
  val minirigs: Resource<List<Minirig>>,
  val openMinirig: (Minirig) -> Unit,
  val applyConfig: (Minirig) -> Unit
)

@Provide fun minirigsModel(
  navigator: Navigator,
  repository: MinirigRepository,
  S: NamedCoroutineScope<KeyUiScope>
) = state {
  MinirigsModel(
    minirigs = repository.minirigs.bindResource(),
    openMinirig = action { minirig -> navigator.push(ConfigKey(minirig.address)) },
    applyConfig = action { minirig ->
      val config = navigator.push(ConfigPickerKey) ?: return@action
      repository.updateConfig(config.copy(id = minirig.address))
    }
  )
}
