/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.injekt.Provide

@Provide class LinkupUseCases(private val remote: MinirigRemote) {
  suspend fun twsPair(address: String) = remote.withMinirig(address) {
    send("P")
  }

  suspend fun startLinkup(address: String) = remote.withMinirig(address) {
    send("H")
  }

  suspend fun joinLinkup(address: String) = remote.withMinirig(address) {
    send("I")
  }

  suspend fun cancelLinkup(address: String) = remote.withMinirig(address) {
    send("J")
  }
}
