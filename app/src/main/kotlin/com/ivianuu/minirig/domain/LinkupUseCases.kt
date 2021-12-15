/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.injekt.*
import kotlinx.coroutines.*

@Provide class LinkupUseCases(private val remote: MinirigRemote) {
  suspend fun startLinkup(address: String) = remote.withMinirig(address) {
    send("HBROADCAST_START")
  }

  suspend fun joinLinkup(address: String) = remote.withMinirig(address) {
    send("IBROADCAST_JOIN")
  }

  suspend fun cancelLinkup(address: String) {
    // disconnect
    remote.withMinirig(address) {
      send("JBROADCAST_LEAVE")
    }

    delay(1000)

    // ensure we get reconnected
    remote.withMinirig(address) {
    }
  }
}
