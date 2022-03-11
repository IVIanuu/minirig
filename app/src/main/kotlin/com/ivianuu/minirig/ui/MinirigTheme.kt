/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.ui

import androidx.compose.ui.graphics.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.rubik.*
import com.ivianuu.essentials.ui.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.injekt.*

object MinirigTheme {
  val Primary = Color(0xFF222F3E)
  val Secondary = Color(0xFFFF9F43)
}

@Provide fun minirigTheme(RP: ResourceProvider) = AppTheme { content ->
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
