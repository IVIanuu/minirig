/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig

import com.ivianuu.injekt.Provide

@Provide class MinirigUseCases(private val remote: MinirigRemote) {
  suspend fun twsPair(address: String) = remote.withMinirig(address) {
    send("P")
  }

  suspend fun cancelTws(address: String) = remote.withMinirig(address) {
    send("J")
  }

  suspend fun powerOff(address: String) = remote.withMinirig(address) {
    send("O")
  }

  suspend fun factoryReset(address: String) = remote.withMinirig(address) {
    send("*")
  }

  suspend fun enablePowerOut(address: String) = remote.withMinirig(address) {
    send("^")
    remote.forceStateRefresh(address)
  }
}
