/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.ui.common.*
import com.ivianuu.essentials.ui.dialog.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.injekt.*
import com.ivianuu.minirig.data.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MinirigPickerKey(val minirigs: List<String>) : DialogKey<String>

@Provide fun minirigPicker(
  key: MinirigPickerKey,
  navigator: Navigator,
  repository: MinirigRepository
) = KeyUi<MinirigPickerKey> {
  DialogScaffold {
    Dialog(
      title = { Text("What minirig should play music?") },
      content = {
        val scope = rememberCoroutineScope()
        val minirigs by produceState(emptyList<Minirig>()) {
          value = key.minirigs
            .parMap {
              repository.minirig(it).first()!!
            }
        }
        VerticalList {
          items(minirigs) { minirig ->
            ListItem(
              modifier = Modifier.clickable {
                scope.launch {
                  navigator.pop(key, minirig.address)
                }
              },
              title = { Text(minirig.name) },
            )
          }
        }
      }
    )
  }
}
