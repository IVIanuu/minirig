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
  val device = bluetoothManager.adapter.getRemoteDevice(address)!!

  if (remote.isConnected(address).first()) return@ConnectToMinirigUseCase true

  a2DPOps.withProxy {
    javaClass.getDeclaredMethod(
      "connect",
      BluetoothDevice::class.java
    ).invoke(this, device)
  }

  remote.isConnected(address).first { it }
}
