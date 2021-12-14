/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.data

import com.ivianuu.essentials.db.*
import kotlinx.serialization.*

@Serializable data class MinirigConfig(
  @PrimaryKey val id: String,
  val band1: Float = 0.5f,
  val band2: Float = 0.5f,
  val band3: Float = 0.5f,
  val band4: Float = 0.5f,
  val band5: Float = 0.5f,
  val gain: Float = 0.5f,
  val auxGain: Float = 0.5f,
  val bassGain: Float = 1f,
  val channel: Float = 0.5f
)
