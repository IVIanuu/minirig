/*
 * Copyright 2021 Manuel Wrage
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivianuu.minirig.ui

import androidx.compose.ui.graphics.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.rubik.*
import com.ivianuu.essentials.ui.*
import com.ivianuu.essentials.ui.animation.transition.*
import com.ivianuu.essentials.ui.material.*
import com.ivianuu.injekt.*

object MinirigTheme {
  val Primary = Color(0xFF5F27CD)
  val Secondary = Color(0xFFFF6B6B)
}

@Provide
fun minirigTheme(RP: ResourceProvider) = AppTheme { content ->
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
    transition = FadeUpwardsStackTransition(),
    content = content
  )
}
