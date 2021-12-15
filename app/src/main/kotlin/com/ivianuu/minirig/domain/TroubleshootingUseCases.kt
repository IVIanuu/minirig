/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import kotlinx.coroutines.*

@Provide class TroubleshootingUseCases(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val remote: MinirigRemote
) {
  suspend fun powerOff(address: String) = remote.withMinirig(address) { socket ->
    socket.outputStream.write("OPOWER_TOGGLE".toByteArray())
  }

  suspend fun rename(address: String, newName: String) = remote.withMinirig(address) { socket ->
    socket.outputStream.write("N SET NAME=$newName".toByteArray())
    delay(500)
    socket.outputStream.write("N SET NAME_SHORT=${newName.take(7)}".toByteArray())
    delay(500)
    socket.outputStream.write("N WRITEN WRITE".toByteArray())
    delay(1000)
    socket.outputStream.write("OPOWER_TOGGLE".toByteArray())
    delay(500)
    val device = bluetoothManager.adapter.getRemoteDevice(address)
    BluetoothDevice::class.java.getDeclaredMethod("setAlias", String::class.java)
      .invoke(device, newName)
  }

  suspend fun clearPairedDevices(address: String) = remote.withMinirig(address) { socket ->
    socket.outputStream.write("N UNPAIRCLEAR_PAIRED".toByteArray())
  }

  suspend fun factoryReset(address: String) = remote.withMinirig(address) { socket ->
    socket.outputStream.write("*RESET".toByteArray())
  }
}
