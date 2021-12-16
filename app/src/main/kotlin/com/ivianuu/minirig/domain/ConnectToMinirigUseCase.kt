/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import kotlinx.coroutines.flow.*

@Provide class MinirigConnectionUseCases(
  private val a2DPOps: A2DPOps,
  private val bluetoothManager: @SystemService BluetoothManager,
  private val remote: MinirigRemote,
  private val L: Logger
) {
  suspend fun connectMinirig(address: String): Boolean {
    val device = bluetoothManager.adapter.getRemoteDevice(address)!!

    if (remote.isConnected(address).first()) return true

    a2DPOps.withProxy("connect minirig") {
      javaClass.getDeclaredMethod(
        "connect",
        BluetoothDevice::class.java
      ).invoke(this, device)
    }

    return remote.isConnected(address).first { it }
  }

  suspend fun disconnectMinirig(address: String) {
    a2DPOps.withProxy("disconnect minirig") {
      javaClass.getDeclaredMethod(
        "disconnect",
        BluetoothDevice::class.java
      ).invoke(this, bluetoothManager.adapter.getRemoteDevice(address)!!)
    }
  }
}
