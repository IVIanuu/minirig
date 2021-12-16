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
import com.ivianuu.injekt.common.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Provide @Scoped<AppScope> class A2DPOps(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val appContext: AppContext,
  private val ioContext: IOContext,
  private val L: Logger,
  private val scope: NamedCoroutineScope<AppScope>
) {
  private val proxy = RefCountedResource<Unit, BluetoothA2dp>(
    timeout = 2.seconds,
    create = {
      log { "acquire proxy" }
      suspendCancellableCoroutine { cont ->
        bluetoothManager.adapter.getProfileProxy(
          appContext,
          object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
              catch { cont.resume(proxy.cast()) }
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

  suspend fun <R> withProxy(
    jobName: String,
    block: suspend BluetoothA2dp.() -> R
  ): R? = runJob(jobName) {
    withContext(ioContext) { proxy.withResource(Unit, block) }
  }
}
