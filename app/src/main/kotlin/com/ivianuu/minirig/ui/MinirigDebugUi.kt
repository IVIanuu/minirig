/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivianuu.essentials.app.AppForegroundState
import com.ivianuu.essentials.coroutines.par
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.state.action
import com.ivianuu.essentials.state.bind
import com.ivianuu.essentials.time.seconds
import com.ivianuu.essentials.ui.common.VerticalList
import com.ivianuu.essentials.ui.insets.ConsumeInsets
import com.ivianuu.essentials.ui.insets.InsetsPadding
import com.ivianuu.essentials.ui.material.Scaffold
import com.ivianuu.essentials.ui.material.TopAppBar
import com.ivianuu.essentials.ui.navigation.Key
import com.ivianuu.essentials.ui.navigation.Model
import com.ivianuu.essentials.ui.navigation.ModelKeyUi
import com.ivianuu.injekt.Provide
import com.ivianuu.minirig.data.MinirigRuntimeData
import com.ivianuu.minirig.domain.MinirigRemote
import com.ivianuu.minirig.domain.parseEq
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive

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
          output.lines().forEach { line ->
            item {
              Text(line)
            }
          }
        }
      }

      LaunchedEffect(output) {
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
              sendMessage(message)
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
  L: Logger
) = Model {
  MinirigDebugModel(
    output = appForegroundState
      .transformLatest {
        if (it == AppForegroundState.FOREGROUND) {
          remote.withMinirig(key.address) {
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
                    message.startsWith("B") ->
                      emit("B: ${message.removePrefix("B")}")
                  }
                }
              },
              {
                while (currentCoroutineContext().isActive) {
                  send("o")
                  send("/")
                  send("q p 00 50")
                  send("B")

                  delay(5.seconds)
                }
              }
            )
          }
        }
      }
      .scan("") { acc, next -> acc + "\n$next" }
      .bind(""),
    sendMessage = action { message ->
      remote.withMinirig(key.address) {
        send(message)
      }
    }
  )
}