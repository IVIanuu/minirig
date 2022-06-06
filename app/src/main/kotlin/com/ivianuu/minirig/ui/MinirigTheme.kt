/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.ui.graphics.Color
import com.ivianuu.essentials.ResourceProvider
import com.ivianuu.essentials.rubik.Rubik
import com.ivianuu.essentials.ui.AppTheme
import com.ivianuu.essentials.ui.material.EsTheme
import com.ivianuu.essentials.ui.material.EsTypography
import com.ivianuu.essentials.ui.material.colors
import com.ivianuu.essentials.ui.material.editEach
import com.ivianuu.injekt.Provide

object MinirigTheme {
  val Primary = Color(0xFF222F3E)
  val Secondary = Color(0xFFFF9F43)
}

@Provide fun minirigTheme(resourceProvider: ResourceProvider) = AppTheme { content ->
  EsTheme(
    lightColors = colors(
      isLight = true,
      primary = MinirigTheme.Primary,
      primaryVariant = MinirigTheme.Primary,
      secondary = MinirigTheme.Secondary,
      secondaryVariant = MinirigTheme.Secondary
    ),
    darkColors = colors(
      isLight = false,
      primary = MinirigTheme.Primary,
      primaryVariant = MinirigTheme.Primary,
      secondary = MinirigTheme.Secondary,
      secondaryVariant = MinirigTheme.Secondary
    ),
    typography = EsTypography.editEach { copy(fontFamily = Rubik) },
    content = content
  )
}
