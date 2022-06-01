/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.app.NotificationManager
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.foreground.ForegroundManager
import com.ivianuu.essentials.foreground.startForeground
import com.ivianuu.essentials.util.NotificationFactory
import com.ivianuu.injekt.Provide

@Provide fun foregroundWorker(
  foregroundManager: ForegroundManager,
  notificationFactory: NotificationFactory
) = ScopeWorker<AppScope> {
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
