/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.db.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.flow.*

@Provide class ConfigRepository(
  private val context: IOContext,
  private val db: Db,
  private val L: Logger
) {
  val configs: Flow<List<MinirigConfig>>
    get() = db.selectAll()

  fun config(id: String): Flow<MinirigConfig?> = db.selectById<MinirigConfig>(id)
    .map { config ->
      config ?: if (id.isMinirigAddress())
        MinirigConfig(id = id)
          .also { updateConfig(it) }
      else config
    }
    .distinctUntilChanged()
    .flowOn(context)

  suspend fun updateConfig(config: MinirigConfig) = db.transaction {
    log { "update config $config" }
    db.insert(config, InsertConflictStrategy.REPLACE)
  }

  suspend fun deleteConfig(id: String) = db.transaction {
    log { "delete config $id" }
    db.deleteById<MinirigConfig>(id)
  }
}
