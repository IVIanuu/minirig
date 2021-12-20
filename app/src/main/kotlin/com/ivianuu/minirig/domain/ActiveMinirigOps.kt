/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.time.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.flow.*

@Provide class ActiveMinirigOps(
  private val a2DPOps: A2DPOps,
  private val bluetoothManager: @SystemService BluetoothManager,
  private val connectionUseCases: MinirigConnectionUseCases,
  private val remote: MinirigRemote,
  private val L: Logger
) {
  val activeMinirig: Flow<String?>
    get() = merge(
      timer(5.seconds),
      remote.bondedDeviceChanges()
    )
      .transformLatest {
        a2DPOps.withProxy("active minirig") {
          emit(
            javaClass.getDeclaredMethod("getActiveDevice")
              .invoke(this)
              .safeAs<BluetoothDevice?>()
              ?.address
              ?.takeIf { it.isMinirigAddress() }
          )
        }
      }
      .distinctUntilChanged()

  suspend fun setActiveMinirig(address: String) {
    a2DPOps.withProxy("set active minirig") {
      connectionUseCases.connectMinirig(address)
      val device = bluetoothManager.adapter.getRemoteDevice(address)!!
      javaClass.getDeclaredMethod("setActiveDevice", BluetoothDevice::class.java)
        .invoke(this, device)
    }
  }
}
