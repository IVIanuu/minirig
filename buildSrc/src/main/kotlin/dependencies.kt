/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName", "unused")

object Build {
  const val applicationId = "com.ivianuu.minirig"
  const val compileSdk = 31
  const val minSdk = 30
  const val targetSdk = 30
  const val versionCode = 1
  const val versionName = "0.0.1"
}

object Deps {
  object Essentials {
    private const val version = "0.0.1-dev1030"
    const val android = "com.ivianuu.essentials:essentials-android:$version"
    const val gradlePlugin = "com.ivianuu.essentials:essentials-gradle-plugin:$version"
    const val rubik = "com.ivianuu.essentials:essentials-rubik:$version"
  }
}
