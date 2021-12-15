/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.injekt.*

@Provide class LinkupUseCases(private val remote: MinirigRemote) {
  suspend fun startLinkup(address: String) = remote.withMinirig(address) { socket ->
    socket.outputStream.write("HBROADCAST_START".toByteArray())
  }

  suspend fun joinLinkup(address: String) = remote.withMinirig(address) { socket ->
    socket.outputStream.write("IBROADCAST_JOIN".toByteArray())
  }

  suspend fun cancelLinkup(address: String) = remote.withMinirig(address) { socket ->
    socket.outputStream.write("JBROADCAST_LEAVE".toByteArray())
  }
}
