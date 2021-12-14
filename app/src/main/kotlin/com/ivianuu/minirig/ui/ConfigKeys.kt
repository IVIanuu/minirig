/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import com.ivianuu.essentials.ui.common.*
import com.ivianuu.essentials.ui.dialog.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.injekt.*
import com.ivianuu.minirig.data.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun ConfigIdPickerKey() =
  TextInputKey(label = "Specify the name of your config..", allowEmpty = false)

object ConfigPickerKey : DialogKey<MinirigConfig>

@Provide fun configPickerUi(
  key: ConfigPickerKey,
  navigator: Navigator,
  repository: MinirigRepository
) = KeyUi<ConfigPickerKey> {
  DialogScaffold {
    Dialog(
      content = {
        val scope = rememberCoroutineScope()
        val configs = remember {
          repository.configs
            .map { configs ->
              configs
                .filter { !it.id.isMinirigAddress() }
            }
        }.collectAsState(emptyList()).value
        VerticalList {
          items(configs) { config ->
            ListItem(
              modifier = Modifier.clickable {
                scope.launch {
                  navigator.pop(key, config)
                }
              },
              title = { Text(config.id) },
            )
          }
        }
      }
    )
  }
}
