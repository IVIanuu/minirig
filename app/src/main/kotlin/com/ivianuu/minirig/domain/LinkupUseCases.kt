/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.coroutines.parForEach
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.time.seconds
import com.ivianuu.injekt.Provide
import kotlinx.coroutines.delay

@Provide class LinkupUseCases(
  private val activeMinirigOps: ActiveMinirigOps,
  private val connectionUseCases: MinirigConnectionUseCases,
  private val remote: MinirigRemote,
  private val L: Logger
) {
  suspend fun startLinkup(hostAddress: String, guestAddresses: List<String>) {
    log { "start linkup host: $hostAddress guests: $guestAddresses" }

    startLinkup(hostAddress)
    guestAddresses.parForEach { joinLinkup(it) }

    delay(5.seconds)

    log { "reconnect to guests" }
    guestAddresses.parForEach { connectionUseCases.connectMinirig(it) }

    delay(1.seconds)

    log { "reconnect to host $hostAddress" }
    activeMinirigOps.setActiveMinirig(hostAddress)
  }

  suspend fun startLinkup(address: String) = remote.withMinirig(address) {
    send("H")
  }

  suspend fun joinLinkup(address: String) = remote.withMinirig(address) {
    send("I")
  }

  suspend fun cancelLinkup(address: String) {
    // disconnect
    remote.withMinirig(address) {
      send("J")
    }

    delay(10.seconds)

    connectionUseCases.connectMinirig(address)
  }

  suspend fun twsPair(address: String) = remote.withMinirig(address) {
    send("P")
  }
}
