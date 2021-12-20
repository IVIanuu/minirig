/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.ivianuu.essentials.app.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.ui.common.*
import com.ivianuu.essentials.ui.insets.*
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.*
import com.ivianuu.injekt.*
import com.ivianuu.minirig.data.*
import com.ivianuu.minirig.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MinirigDebugKey(val address: String) : Key<Unit>

@Provide val minirigDebugUi = ModelKeyUi<MinirigDebugKey, MinirigDebugModel> {
  Scaffold(
    topBar = { TopAppBar(title = { Text("Debug TODO") }) }
  ) {
    Column(
      modifier = Modifier.fillMaxSize()
        .padding(16.dp)
    ) {
      var isOnBottom by remember { mutableStateOf(true) }

      val listState = rememberLazyListState()

      val isNowOnBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ==
          listState.layoutInfo.totalItemsCount - 1
      LaunchedEffect(isNowOnBottom) {
        isOnBottom = isNowOnBottom
      }

      ConsumeInsets(bottom = true) {
        VerticalList(
          modifier = Modifier.weight(1f),
          state = listState
        ) {
          model.output.lines().forEach { line ->
            item {
              Text(line)
            }
          }
        }
      }

      LaunchedEffect(model.output) {
        if (isOnBottom)
          listState.scrollToItem(listState.layoutInfo.totalItemsCount)
      }

      var message by remember { mutableStateOf("") }
      InsetsPadding {
        Row {
          TextField(
            modifier = Modifier.weight(1f),
            value = message,
            onValueChange = { message = it }
          )

          Button(
            modifier = Modifier.padding(start = 8.dp),
            onClick = {
              model.sendMessage(message)
              message = ""
            }
          ) {
            Text("SEND")
          }
        }
      }
    }
  }
}

data class MinirigDebugModel(
  val output: String,
  val sendMessage: (String) -> Unit
)

@Provide fun minirigDebugModel(
  appForegroundState: Flow<AppForegroundState>,
  key: MinirigDebugKey,
  remote: MinirigRemote,
  SS: StateScope,
  L: Logger
) = MinirigDebugModel(
  output = appForegroundState
    .transformLatest {
      if (it == AppForegroundState.FOREGROUND) {
        remote.withMinirig(key.address, "debug messages") {
          par(
            {
              var lastRuntimeData1: String? = null
              var lastRuntimeData2: String? = null

              suspend fun emitRuntimeDataIfPossible() {
                if (lastRuntimeData1 != null && lastRuntimeData2 != null)
                  emit(MinirigRuntimeData(lastRuntimeData1!!, lastRuntimeData2!!).toString())
              }

              messages.collect { message ->
                when {
                  message.startsWith("o") -> {
                    lastRuntimeData1 = message
                    emitRuntimeDataIfPossible()
                  }
                  message.startsWith("/") -> {
                    lastRuntimeData2 = message
                    emitRuntimeDataIfPossible()
                  }
                  message.startsWith("q") ->
                    emit("eq: ${message.parseEq().toString().removeSurrounding("{", "}")}")
                }
              }
            },
            {
              while (currentCoroutineContext().isActive) {
                send("oGET_RUNTIME_DATA")
                send("/GET_RUNTIME_DATA2")
                send("q p 00 50")

                delay(5000)
              }
            }
          )
        }
      }
    }
    .scan("") { acc, next -> acc + "\n$next" }
    .bind(""),
  sendMessage = action { message ->
    remote.withMinirig(key.address, "send message") {
      send(message)
    }
  }
)
