/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.app.NotificationManager
import android.media.AudioManager
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.coroutines.par
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.foreground.ForegroundManager
import com.ivianuu.essentials.foreground.startForeground
import com.ivianuu.essentials.logging.Logger
import com.ivianuu.essentials.logging.log
import com.ivianuu.essentials.time.milliseconds
import com.ivianuu.essentials.util.BroadcastsFactory
import com.ivianuu.essentials.util.NotificationFactory
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import com.ivianuu.minirig.data.MinirigPrefs
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart

@Provide fun minirigGainController(
  audioManager: @SystemService AudioManager,
  broadcastsFactory: BroadcastsFactory,
  foregroundManager: ForegroundManager,
  logger: Logger,
  notificationFactory: NotificationFactory,
  pref: DataStore<MinirigPrefs>
) = ScopeWorker<AppScope> {
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

          pref.updateData { copy(gain = gain, auxGain = gain) }
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
}
