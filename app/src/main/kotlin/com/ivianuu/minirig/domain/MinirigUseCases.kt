/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.injekt.Provide

context(MinirigRemote) @Provide class MinirigUseCases {
  suspend fun twsPair(address: String) = withMinirig(address) {
    send("P")
  }

  suspend fun cancelTws(address: String) = withMinirig(address) {
    send("J")
  }

  suspend fun powerOff(address: String) = withMinirig(address) {
    send("O")
  }

  suspend fun factoryReset(address: String) = withMinirig(address) {
    send("*")
  }
}
