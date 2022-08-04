/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import com.github.michaelbull.result.onFailure
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.catch
import com.ivianuu.essentials.coroutines.parForEach
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.ui.UiScope
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import com.ivianuu.minirig.data.TwsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

@SuppressLint("MissingPermission")
@Provide fun hdAudioSettingSynchronizer(
  a2DPOps: A2DPOps,
  bluetoothManager: @SystemService BluetoothManager,
  minirigRepository: MinirigRepository,
  L: Logger
) = ScopeWorker<UiScope> {
  return@ScopeWorker
  minirigRepository.minirigs.collectLatest { minirigs ->
    minirigs.parForEach { minirig ->
      minirigRepository.minirigState(minirig.address)
        .map { it.twsState == TwsState.NONE }
        .distinctUntilChanged()
        .collectLatest { shouldBeAptXHd ->
          while (coroutineContext.isActive) {
            catch {
              a2DPOps.withProxy {
                val device = bluetoothManager.adapter.bondedDevices
                  .single { it.address == minirig.address }

                javaClass
                  .declaredMethods
                  .single { it.name == "setOptionalCodecsEnabled" }
                  .invoke(this, device, 1)

                val codecStatus = javaClass.declaredMethods
                  .single { it.name == "getCodecStatus" }
                  .invoke(this, device)

                val isAptXHd = codecStatus.javaClass
                  .declaredMethods
                  .single { it.name == "getCodecConfig" }
                  .invoke(codecStatus)
                  .let { codecConfig ->
                    codecConfig.javaClass
                      .declaredMethods
                      .single { it.name == "getCodecType" }
                      .invoke(codecConfig) == 2
                  }

                if (shouldBeAptXHd != isAptXHd) {
                  val newCodecConfig = codecStatus.javaClass.declaredMethods
                    .single { it.name == "getCodecsSelectableCapabilities" }
                    .invoke(codecStatus)
                    .let { it as Array<Any> }
                    .singleOrNull { capability ->
                      capability.javaClass
                        .declaredMethods
                        .single { it.name == "getCodecType" }
                        .invoke(capability) == 2
                    }

                  log { "new config $isAptXHd $shouldBeAptXHd $newCodecConfig" }

                  if (newCodecConfig != null) {
                    javaClass
                      .declaredMethods
                      .single { it.name == "setCodecConfigPreference" }
                      .invoke(
                        this,
                        device,
                        newCodecConfig
                      )
                  }
                }
              }
            }.onFailure { it.printStackTrace() }

            delay(5000)
          }
        }
    }
  }
}
