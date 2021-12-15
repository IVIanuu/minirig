/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.flow.*

fun interface ConnectToMinirigUseCase : suspend (String) -> Boolean

@Provide fun connectToMinirigUseCase(
  a2DPOps: A2DPOps,
  bluetoothManager: @SystemService BluetoothManager,
  remote: MinirigRemote,
  L: Logger
) = ConnectToMinirigUseCase { address ->
  a2DPOps.withProxy {
    val device = bluetoothManager.adapter.getRemoteDevice(address)!!
    log { "pre connect ${device.debugName()}" }
    val result = javaClass.getDeclaredMethod(
      "connect",
      BluetoothDevice::class.java
    ).invoke(this, device)
    log { "connect result ${device.debugName()} $result" }
  }

  remote.isConnected(address).first { it }
}
