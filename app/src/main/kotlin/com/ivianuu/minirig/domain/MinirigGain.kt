/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.app.NotificationManager
import android.media.AudioManager
import com.ivianuu.essentials.coroutines.par
import com.ivianuu.essentials.foreground.ForegroundManager
import com.ivianuu.essentials.foreground.startForeground
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.time.milliseconds
import com.ivianuu.essentials.util.BroadcastsFactory
import com.ivianuu.essentials.util.NotificationFactory
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

@JvmInline value class MinirigGain(val value: Float)

@Provide fun minirigGain(
  audioManager: @SystemService AudioManager,
  broadcastsFactory: BroadcastsFactory,
  foregroundManager: ForegroundManager,
  logger: Logger,
  notificationFactory: NotificationFactory
): Flow<MinirigGain> = channelFlow {
  par(
    {
      broadcastsFactory("android.media.VOLUME_CHANGED_ACTION")
        .onStart<Any?> { emit(Unit) }
        .debounce(200.milliseconds)
        .collect {
          val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
          val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()

          val gain = volume / maxVolume

          log { "volume changed: current volume $volume max volume is $maxVolume gain = $gain" }

          send(MinirigGain(gain))
        }
    },
    {
      foregroundManager.startForeground(
        1,
        notificationFactory.build(
          "foreground",
          "Foreground",
          NotificationManager.IMPORTANCE_LOW
        ) {
          setContentTitle("Minirig")
        }
      )
    }
  )
}.distinctUntilChanged()
