/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService

@Provide class MinirigConnectionUseCases(
  private val a2DPOps: A2DPOps,
  private val bluetoothManager: @SystemService BluetoothManager,
  private val L: Logger
) {
  suspend fun connectMinirig(address: String) {
    a2DPOps.withProxy {
      BluetoothA2dp::class.java.getDeclaredMethod(
        "connect",
        BluetoothDevice::class.java
      ).invoke(this, bluetoothManager.adapter.getRemoteDevice(address)!!)
    }
  }

  suspend fun disconnectMinirig(address: String) {
    a2DPOps.withProxy {
      BluetoothA2dp::class.java.getDeclaredMethod(
        "disconnect",
        BluetoothDevice::class.java
      ).invoke(this, bluetoothManager.adapter.getRemoteDevice(address)!!)
    }
  }
}
