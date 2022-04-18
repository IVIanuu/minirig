/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import com.ivianuu.essentials.AppContext
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.catch
import com.ivianuu.essentials.coroutines.RefCountedResource
import com.ivianuu.essentials.coroutines.withResource
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.time.seconds
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import com.ivianuu.injekt.common.Scoped
import com.ivianuu.injekt.coroutines.IOContext
import com.ivianuu.injekt.coroutines.NamedCoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Provide @Scoped<AppScope> class A2DPOps(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val appContext: AppContext,
  private val ioContext: IOContext,
  private val L: Logger,
  private val scope: NamedCoroutineScope<AppScope>
) {
  private val proxy = RefCountedResource<Unit, BluetoothA2dp>(
    scope = scope,
    timeout = 2.seconds,
    create = {
      log { "acquire proxy" }
      suspendCancellableCoroutine { cont ->
        bluetoothManager.adapter.getProfileProxy(
          appContext,
          object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
              catch { cont.resume(proxy as BluetoothA2dp) }
            }

            override fun onServiceDisconnected(profile: Int) {
            }
          },
          BluetoothProfile.A2DP
        )
      }
    },
    release = { _, proxy ->
      log { "release proxy" }
      catch {
        bluetoothManager.adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
      }
    }
  )

  suspend fun <R> withProxy(block: suspend BluetoothA2dp.() -> R): R? =
    withContext(ioContext) { proxy.withResource(Unit, block) }
}
