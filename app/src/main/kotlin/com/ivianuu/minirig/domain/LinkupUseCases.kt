/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import kotlinx.coroutines.*

@Provide class LinkupUseCases(
  private val activeMinirigOps: ActiveMinirigOps,
  private val connectToMinirigUseCase: ConnectToMinirigUseCase,
  private val remote: MinirigRemote,
  private val L: Logger
) {
  suspend fun startLinkup(hostAddress: String, guestAddresses: List<String>) {
    log { "start linkup host: $hostAddress guests: $guestAddresses" }

    startLinkup(hostAddress)
    guestAddresses.forEach { joinLinkup(it) }

    delay(5000)

    log { "reconnect to guests" }
    guestAddresses.parForEach { connectToMinirigUseCase(it) }

    delay(1000)

    log { "reconnect to host $hostAddress" }
    activeMinirigOps.setActiveMinirig(hostAddress)
  }

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

    delay(10000)

    connectToMinirigUseCase(address)
  }
}
