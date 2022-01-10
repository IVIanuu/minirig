/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import com.ivianuu.essentials.ui.dialog.*
import com.ivianuu.minirig.data.*

fun ConfigIdPickerKey() =
  TextInputKey(label = "Specify the name of your config..", predicate = { it.isNotEmpty() })

fun RenameMinirigKey() =
  TextInputKey(label = "Specify the new name for the minirig..", predicate = { it.isNotEmpty() })

fun ConfigPickerKey(configs: List<MinirigConfig>) = ListKey(
  items = configs.map { ListKey.Item(it, it.id) }
)

fun MinirigPickerKey(minirigs: List<Minirig>) = ListKey(
  items = minirigs.map { ListKey.Item(it, it.name) }
)
