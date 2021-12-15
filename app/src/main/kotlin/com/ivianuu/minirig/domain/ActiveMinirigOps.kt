/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Provide class ActiveMinirigOps(
  private val a2DPOps: A2DPOps,
  private val bluetoothManager: @SystemService BluetoothManager,
  private val connectToMinirigUseCase: ConnectToMinirigUseCase,
  private val L: Logger
) {
  val activeMinirig: Flow<String?>
    get() = channelFlow {
      a2DPOps.withProxy {
        while (currentCoroutineContext().isActive) {
          send(
            javaClass.getDeclaredMethod("getActiveDevice")
              .invoke(this)
              .safeAs<BluetoothDevice?>()
              ?.address
              ?.takeIf { it.isMinirigAddress() }
          )
          delay(1000)
        }
      }
    }.distinctUntilChanged()

  suspend fun setActiveMinirig(address: String) {
    a2DPOps.withProxy {
      connectToMinirigUseCase(address)
      val device = bluetoothManager.adapter.getRemoteDevice(address)!!
      javaClass.getDeclaredMethod("setActiveDevice", BluetoothDevice::class.java)
        .invoke(this, device)
    }
  }
}
