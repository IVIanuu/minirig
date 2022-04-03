/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivianuu.essentials.permission.PermissionRequester
import com.ivianuu.essentials.ui.animation.AnimatedBox
import com.ivianuu.essentials.ui.insets.InsetsPadding
import com.ivianuu.essentials.ui.navigation.KeyUiScope
import com.ivianuu.essentials.ui.navigation.RootKey
import com.ivianuu.essentials.ui.navigation.SimpleKeyUi
import com.ivianuu.essentials.ui.systembars.overlaySystemBarBgColor
import com.ivianuu.essentials.ui.systembars.systemBarStyle
import com.ivianuu.essentials.ui.util.isLight
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.common.Scope
import com.ivianuu.injekt.common.typeKeyOf
import com.ivianuu.minirig.domain.MinirigBluetoothConnectPermission
import kotlinx.coroutines.delay

@Provide object HomeKey : RootKey

@Provide fun homeUi(
  minirigsUi: MinirigsUi,
  configsUi: ConfigsUi,
  permissionRequester: PermissionRequester,
  scope: Scope<KeyUiScope>
) = SimpleKeyUi<HomeKey> {
  val pages = (0..1).toList()
  var selectedPage by scope { mutableStateOf(pages.first()) }

  LaunchedEffect(true) {
    // todo remove once essentials state is fixed
    delay(1000)
    permissionRequester(listOf(typeKeyOf<MinirigBluetoothConnectPermission>()))
  }

  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    AnimatedBox(
      modifier = Modifier.weight(1f),
      current = selectedPage
    ) { page ->
      when (page) {
        0 -> minirigsUi()
        1 -> configsUi()
        else -> throw AssertionError("Unexpected page $page")
      }
    }

    Surface(
      modifier = Modifier
        .systemBarStyle(
          bgColor = overlaySystemBarBgColor(MaterialTheme.colors.primary),
          lightIcons = MaterialTheme.colors.primary.isLight,
          elevation = 8.dp
        ),
      elevation = 8.dp,
      color = MaterialTheme.colors.primary
    ) {
      InsetsPadding(left = false, top = false, right = false) {
        BottomNavigation(
          backgroundColor = MaterialTheme.colors.primary,
          elevation = 0.dp
        ) {
          for (page in pages) {
            BottomNavigationItem(
              alwaysShowLabel = false,
              selected = selectedPage == page,
              onClick = { selectedPage = page },
              icon = {
                Text(
                  when (page) {
                    0 -> "Minirigs"
                    1 -> "Configs"
                    else -> throw AssertionError("Unexpected page $page")
                  }
                )
              },
            )
          }
        }
      }
    }
  }
}
