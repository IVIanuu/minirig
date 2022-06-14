/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.provider.Settings
import com.ivianuu.essentials.AppContext
import com.ivianuu.essentials.app.AppForegroundScope
import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.shell.Shell
import com.ivianuu.injekt.Provide
import com.ivianuu.minirig.data.MinirigPrefs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Provide fun monoSettingSynchronizer(
  context: AppContext,
  pref: DataStore<MinirigPrefs>,
  shell: Shell
) = ScopeWorker<AppForegroundScope> {
  pref.data
    .map { if (it.mono) 1 else 0 }
    .distinctUntilChanged()
    .collect { appMono ->
      val androidMono = Settings.System.getInt(context.contentResolver, "master_mono", 0)
      if (appMono != androidMono)
        shell.run("settings put system master_mono $appMono")
    }
}
