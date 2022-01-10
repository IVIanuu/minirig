/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*

@Provide class MinirigConnectionUseCases(
  private val a2DPOps: A2DPOps,
  private val bluetoothManager: @SystemService BluetoothManager,
  private val remote: MinirigRemote,
  private val L: Logger
) {
  suspend fun connectMinirig(address: String) {
    remote.withMinirig(address, "connect minirig") {
    }
  }

  suspend fun disconnectMinirig(address: String) {
    a2DPOps.withProxy("disconnect minirig") {
      BluetoothA2dp::class.java.getDeclaredMethod(
        "disconnect",
        BluetoothDevice::class.java
      ).invoke(this, bluetoothManager.adapter.getRemoteDevice(address)!!)
    }
  }
}
