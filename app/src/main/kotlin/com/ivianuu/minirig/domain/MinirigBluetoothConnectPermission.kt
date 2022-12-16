/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.Manifest
import com.ivianuu.essentials.app.AppForegroundScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.permission.PermissionRequester
import com.ivianuu.essentials.permission.runtime.RuntimePermission
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.common.typeKeyOf

@Provide class MinirigBluetoothConnectPermission : RuntimePermission(
  permissionName = Manifest.permission.BLUETOOTH_CONNECT,
  title = "Bluetooth"
)

// always request permissions when launching the ui
@Provide fun minirigPermissionRequestWorker(
  permissionRequester: PermissionRequester
) = ScopeWorker<AppForegroundScope> {
  permissionRequester(listOf(typeKeyOf<MinirigBluetoothConnectPermission>()))
}
