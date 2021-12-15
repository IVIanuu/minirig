/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import com.ivianuu.essentials.ui.dialog.*

fun ConfigIdPickerKey() =
  TextInputKey(label = "Specify the name of your config..", allowEmpty = false)

fun RenameMinirigKey() =
  TextInputKey(label = "Specify the new name for the minirig..", allowEmpty = false)
