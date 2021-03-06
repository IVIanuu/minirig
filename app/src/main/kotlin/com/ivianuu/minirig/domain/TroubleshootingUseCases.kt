/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.injekt.Provide

@Provide class TroubleshootingUseCases(private val remote: MinirigRemote) {
  suspend fun powerOff(address: String) = remote.withMinirig(address) {
    send("O")
  }

  suspend fun factoryReset(address: String) = remote.withMinirig(address) {
    send("*")
  }
}
