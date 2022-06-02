/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.Manifest
import androidx.compose.runtime.Composable
import com.ivianuu.essentials.app.AppForegroundScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.permission.PermissionRequester
import com.ivianuu.essentials.permission.runtime.RuntimePermission
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.common.typeKeyOf

@Provide class MinirigBluetoothConnectPermission : RuntimePermission {
  override val permissionName: String
    get() = Manifest.permission.BLUETOOTH_CONNECT
  override val title: String
    get() = "Bluetooth"
  override val icon: (@Composable () -> Unit)?
    get() = null
}

// always request permissions when launching the ui
@Provide fun minirigPermissionRequestWorker(
  permissionRequester: PermissionRequester
) = ScopeWorker<AppForegroundScope> {
  permissionRequester(listOf(typeKeyOf<MinirigBluetoothConnectPermission>()))
}
