/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.ivianuu.essentials.ui.animation.*
import com.ivianuu.essentials.ui.insets.*
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.essentials.ui.systembars.*
import com.ivianuu.essentials.ui.util.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.common.*

@Provide object HomeKey : RootKey

@Provide fun homeUi(
  minirigsUi: MinirigsUi,
  configsUi: ConfigsUi,
  scope: Scope<KeyUiScope>
) = KeyUi<HomeKey> {
  val pages = (0..1).toList()
  var selectedPage by scope { mutableStateOf(pages.first()) }

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
