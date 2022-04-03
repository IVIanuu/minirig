/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.log
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.coroutines.IOContext
import com.ivianuu.minirig.data.MinirigConfig
import com.ivianuu.minirig.data.isMinirigAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Provide class ConfigRepository(
  private val context: IOContext,
  private val configsStore: DataStore<List<MinirigConfig>>,
  private val L: Logger
) {
  val configs: Flow<List<MinirigConfig>>
    get() = configsStore.data

  fun config(id: String): Flow<MinirigConfig?> = configsStore.data
    .map { it.firstOrNull { it.id == id } }
    .map { config ->
      config ?: if (id.isMinirigAddress())
        MinirigConfig(id = id)
          .also { updateConfig(it) }
      else config
    }
    .distinctUntilChanged()
    .flowOn(context)

  suspend fun updateConfig(config: MinirigConfig) = configsStore.updateData {
    log { "update config $config" }
    filter { it.id != config.id } + config
  }

  suspend fun deleteConfig(id: String) = configsStore.updateData {
    log { "delete config $id" }
    filter { it.id != id }
  }
}
