/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import com.google.accompanist.pager.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.injekt.*
import kotlinx.coroutines.*

@Provide object HomeKey : RootKey

@Provide fun homeUi(
  minirigsUi: MinirigsUi,
  configsUi: ConfigsUi
) = KeyUi<HomeKey> {
  val pagerState = rememberPagerState()
  val scope = rememberCoroutineScope()

  val pages = (0..1).toList()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Minirig") },
        bottomContent = {
          TabRow(
            selectedTabIndex = pagerState.currentPage,
            backgroundColor = MaterialTheme.colors.primary,
            indicator = { tabPositions ->
              TabRowDefaults.Indicator(
                Modifier.pagerTabIndicatorOffset(pagerState, tabPositions)
              )
            }
          ) {
            for (page in pages) {
              Tab(
                selected = pagerState.currentPage == page,
                onClick = {
                  scope.launch {
                    pagerState.animateScrollToPage(page)
                  }
                },
                text = {
                  Text(
                    when (page) {
                      0 -> "Minirigs"
                      1 -> "Configs"
                      else -> throw AssertionError("Unexpected page $page")
                    }
                  )
                }
              )
            }
          }
        }
      )
    }
  ) {
    HorizontalPager(count = pages.size, state = pagerState) { currentPage ->
      when (currentPage) {
        0 -> minirigsUi()
        1 -> configsUi()
        else -> throw AssertionError("Unexpected page $currentPage")
      }
    }
  }
}
