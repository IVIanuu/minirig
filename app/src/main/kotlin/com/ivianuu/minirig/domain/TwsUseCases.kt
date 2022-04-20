/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.coroutines.parForEach
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.time.seconds
import com.ivianuu.injekt.Provide
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Provide class TwsUseCases(
  private val activeMinirigUseCases: ActiveMinirigUseCases,
  private val connectionUseCases: MinirigConnectionUseCases,
  private val minirigRepository: MinirigRepository,
  private val remote: MinirigRemote,
  private val L: Logger
) {
  suspend fun twsPair(address: String) {
    activeMinirigUseCases.setActiveMinirig(address)

    minirigRepository.minirigs
      .first()
      .filter { it.address != address }
      .parForEach { connectionUseCases.disconnectMinirig(it.address) }

    delay(2.seconds)

    remote.withMinirig(address) {
      send("P")
    }
  }

  suspend fun cancelTws(address: String) {
    remote.withMinirig(address) {
      send("J")
    }

    delay(2.seconds)

    minirigRepository.minirigs
      .first()
      .parForEach { connectionUseCases.connectMinirig(it.address) }
  }
}
