/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig

import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.Scope
import com.ivianuu.essentials.app.EsApp
import com.ivianuu.essentials.injectWith
import com.ivianuu.essentials.logging.AndroidLogger
import com.ivianuu.injekt.provider

class App : EsApp() {
  override fun buildAppScope(): Scope<AppScope> =
    injectWith(provider(AndroidLogger.Companion::androidLogger))
}
