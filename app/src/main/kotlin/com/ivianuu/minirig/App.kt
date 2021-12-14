/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig

import com.ivianuu.essentials.*
import com.ivianuu.essentials.app.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.common.*

class App : EsApp() {
  override fun buildAppElements(@Inject scope: Scope<AppScope>): Elements<AppScope> =
    @Providers(
      ".**",
      "com.ivianuu.essentials.logging.AndroidLogger"
    ) inject()
}
