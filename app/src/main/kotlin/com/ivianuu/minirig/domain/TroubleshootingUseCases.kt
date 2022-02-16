/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import kotlinx.coroutines.*

@Provide class TroubleshootingUseCases(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val remote: MinirigRemote,
  private val repository: MinirigRepository
) {
  suspend fun enablePowerOut(address: String) = remote.withMinirig(address) {
    send("^")
    delay(300)
    repository.stateChanged(address)
  }

  suspend fun powerOff(address: String) = remote.withMinirig(address) {
    send("O")
  }

  suspend fun rename(address: String, newName: String) = remote.withMinirig(address) {
    send("N SET NAME=$newName")
    send("N SET NAME_SHORT=${newName.take(7)}")
    send("N WRITEN WRITE")
    send("O")
    val device = bluetoothManager.adapter.getRemoteDevice(address)
    BluetoothDevice::class.java.getDeclaredMethod("setAlias", String::class.java)
      .invoke(device, newName)
  }

  suspend fun clearPairedDevices(address: String) = remote.withMinirig(address) {
    send("N UNPAIRCLEAR_PAIRED")
  }

  suspend fun factoryReset(address: String) = remote.withMinirig(address) {
    send("*")
  }
}
