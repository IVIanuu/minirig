/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import kotlin.coroutines.*

fun interface SetOutputDeviceUseCase : suspend (String) -> Unit

@Provide fun setOutputDeviceUseCase(
  bluetoothManager: @SystemService BluetoothManager,
  context: AppContext
) = SetOutputDeviceUseCase { address ->
  suspendCoroutine { cont ->
    val device = bluetoothManager.adapter.getRemoteDevice(address)!!
    bluetoothManager.adapter.getProfileProxy(
      context,
      object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
          proxy.javaClass.getDeclaredMethod(
            "disconnect",
            BluetoothDevice::class.java
          ).invoke(proxy, device)

          proxy.javaClass.getDeclaredMethod("connect", BluetoothDevice::class.java)
            .invoke(proxy, device)

          bluetoothManager.adapter.closeProfileProxy(profile, proxy)

          catch { cont.resume(Unit) }
        }

        override fun onServiceDisconnected(profile: Int) {
        }
      },
      BluetoothProfile.A2DP
    )
  }
}
