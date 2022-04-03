/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.Manifest
import com.ivianuu.essentials.permission.runtime.RuntimePermission
import com.ivianuu.injekt.Provide

@Provide class MinirigBluetoothConnectPermission : RuntimePermission {
  override val permissionName: String
    get() = Manifest.permission.BLUETOOTH_CONNECT
  override val title: String
    get() = "Bluetooth"
}
