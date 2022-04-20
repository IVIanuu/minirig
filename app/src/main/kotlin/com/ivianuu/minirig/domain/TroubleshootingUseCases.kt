/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.injekt.Provide
import kotlinx.coroutines.delay

@Provide class TroubleshootingUseCases(
  private val remote: MinirigRemote,
  private val repository: MinirigRepository
) {
  suspend fun enablePowerOut(address: String) = remote.withMinirig(address) {
    send("^")
    delay(300)
    repository.stateChanged(address)
  }

  suspend fun powerOff(address: String) = remote.withMinirig(address) {
    send("O")
  }

  suspend fun factoryReset(address: String) = remote.withMinirig(address) {
    send("*")
  }
}
