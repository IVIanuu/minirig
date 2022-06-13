/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import com.ivianuu.essentials.app.ScopeWorker
import com.ivianuu.essentials.data.DataStore
import com.ivianuu.essentials.shell.Shell
import com.ivianuu.essentials.ui.UiScope
import com.ivianuu.injekt.Provide
import com.ivianuu.minirig.data.MinirigPrefs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Provide fun monoSettingSynchronizer(
  pref: DataStore<MinirigPrefs>,
  shell: Shell
) = ScopeWorker<UiScope> {
  pref.data
    .map { it.mono }
    .distinctUntilChanged()
    .collect { mono ->
      shell.run("settings put system master_mono ${if (mono) 1 else 0}")
    }
}
