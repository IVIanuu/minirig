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
import com.ivianuu.minirig.data.isMinirigAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest

@Provide class ActiveMinirigOps(
  private val a2DPOps: A2DPOps,
  private val bluetoothManager: @SystemService BluetoothManager,
  private val remote: MinirigRemote,
  private val L: Logger
) {
  val activeMinirig: Flow<String?>
    get() = merge(
      flow<Unit> {
        while (true) {
          emit(Unit)
          delay(5000)
        }
      },
      remote.bondedDeviceChanges()
    )
      .transformLatest {
        a2DPOps.withProxy {
          emit(
            BluetoothA2dp::class.java.getDeclaredMethod("getActiveDevice")
              .invoke(this)
              .let { it as? BluetoothDevice }
              ?.address
              ?.takeIf { it.isMinirigAddress() }
          )
        }
      }
      .distinctUntilChanged()

  suspend fun setActiveMinirig(address: String) {
    remote.withMinirig(address) {
      a2DPOps.withProxy {
        val device = bluetoothManager.adapter.getRemoteDevice(address)!!
        BluetoothA2dp::class.java.getDeclaredMethod("setActiveDevice", BluetoothDevice::class.java)
          .invoke(this, device)
      }
    }
  }
}
