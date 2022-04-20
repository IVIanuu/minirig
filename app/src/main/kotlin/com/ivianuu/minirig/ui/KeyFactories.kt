/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import com.ivianuu.essentials.ui.dialog.ListKey
import com.ivianuu.essentials.ui.dialog.TextInputKey
import com.ivianuu.minirig.data.MinirigConfig

fun ConfigIdPickerKey() =
  TextInputKey(label = "Specify the name of your config..", predicate = { it.isNotEmpty() })

fun RenameMinirigKey() =
  TextInputKey(label = "Specify the new name for the minirig..", predicate = { it.isNotEmpty() })

fun ConfigPickerKey(configs: List<MinirigConfig>) = ListKey(
  items = configs
    .sortedBy { it.id }
    .map { ListKey.Item(it, it.id) }
)
