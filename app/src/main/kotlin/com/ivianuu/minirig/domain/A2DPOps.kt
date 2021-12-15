/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Provide class A2DPOps(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val context: AppContext
) {
  suspend fun <R> withProxy(block: suspend BluetoothA2dp.() -> R): R? =
    suspendCancellableCoroutine<BluetoothA2dp?> { cont ->
      bluetoothManager.adapter.getProfileProxy(
        context,
        object : BluetoothProfile.ServiceListener {
          override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            catch { cont.resume(proxy.cast()) }
          }

          override fun onServiceDisconnected(profile: Int) {
            catch { cont.resume(null) }
          }
        },
        BluetoothProfile.A2DP
      )
    }?.let { proxy ->
      guarantee(
        block = { block(proxy) },
        finalizer = {
          catch {
            bluetoothManager.adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
          }
        }
      )
    }
}
