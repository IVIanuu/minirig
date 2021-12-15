/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.util.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.flow.*

@Provide class ActiveMinirigOps(
  private val a2DPOps: A2DPOps,
  private val bluetoothManager: @SystemService BluetoothManager,
  private val broadcastsFactory: BroadcastsFactory,
  private val connectToMinirigUseCase: ConnectToMinirigUseCase,
  private val L: Logger
) {
  val activeMinirig: Flow<String?>
    get() = broadcastsFactory(
      "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED"
    )
      .map { it.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) }
      .onStart {
        a2DPOps.withProxy {
          emit(
            javaClass.getDeclaredMethod("getActiveDevice")
              .invoke(this)
              .safeAs()
          )
        }
      }
      .map {
        it
          ?.address
          ?.takeIf { it.isMinirigAddress() }
      }
      .distinctUntilChanged()

  suspend fun setActiveMinirig(address: String) {
    a2DPOps.withProxy {
      connectToMinirigUseCase(address)
      val device = bluetoothManager.adapter.getRemoteDevice(address)!!
      val result = javaClass.getDeclaredMethod("setActiveDevice", BluetoothDevice::class.java)
        .invoke(this, device)
      log { "set active device ${device.debugName()} result $result" }
    }
  }
}
