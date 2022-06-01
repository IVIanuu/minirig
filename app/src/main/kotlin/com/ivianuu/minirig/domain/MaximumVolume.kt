/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.media.AudioManager
import com.ivianuu.essentials.time.milliseconds
import com.ivianuu.essentials.util.BroadcastsFactory
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@JvmInline value class MaximumVolume(val value: Boolean)

@Provide fun maximumVolume(
  audioManager: @SystemService AudioManager,
  broadcastsFactory: BroadcastsFactory
): Flow<MaximumVolume> = broadcastsFactory("android.media.VOLUME_CHANGED_ACTION")
  .onStart<Any?> { emit(Unit) }
  .debounce(200.milliseconds)
  .map {
    val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    MaximumVolume(volume == maxVolume)
  }
  .distinctUntilChanged()
