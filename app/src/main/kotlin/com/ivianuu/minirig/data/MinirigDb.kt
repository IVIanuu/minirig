/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.data

import com.ivianuu.essentials.*
import com.ivianuu.essentials.db.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.coroutines.*

@Provide fun minirigDb(
  androidContext: AppContext,
  ioContext: IOContext
): Db = AndroidDb(
  context = androidContext,
  coroutineContext = ioContext,
  name = "minirig.db",
  schema = Schema(
    version = 1,
    entities = listOf(EntityDescriptor<MinirigConfig>(tableName = "configs"))
  )
)
