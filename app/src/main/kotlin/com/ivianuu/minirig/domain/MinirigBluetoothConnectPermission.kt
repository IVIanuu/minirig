/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.*
import androidx.compose.runtime.*
import com.ivianuu.essentials.permission.runtime.*
import com.ivianuu.injekt.*

@Provide class MinirigBluetoothConnectPermission : RuntimePermission {
  override val permissionName: String
    get() = Manifest.permission.BLUETOOTH_CONNECT
  override val title: String
    get() = "Bluetooth"

  @Composable override fun Icon() {
  }
}
