/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.BluetoothManager
import com.ivianuu.essentials.AppScope
import com.ivianuu.essentials.permission.PermissionManager
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.common.Scoped
import com.ivianuu.injekt.common.typeKeyOf
import com.ivianuu.injekt.coroutines.IOContext
import com.ivianuu.minirig.data.Minirig
import com.ivianuu.minirig.data.isMinirig
import com.ivianuu.minirig.data.toMinirig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

context(BluetoothManager, MinirigRemote, PermissionManager)
@Provide @Scoped<AppScope> class MinirigRepository(private val context: IOContext) {
  val minirigs: Flow<List<Minirig>>
    get() = permissionState(listOf(typeKeyOf<MinirigBluetoothConnectPermission>()))
      .flatMapLatest {
        if (!it) flowOf(emptyList())
        else bondedDeviceChanges()
          .onStart<Any> { emit(Unit) }
          .map {
            adapter?.bondedDevices
              ?.filter { it.isMinirig() }
              ?.map { it.toMinirig() }
              ?: emptyList()
          }
          .distinctUntilChanged()
          .flowOn(context)
      }
      .distinctUntilChanged()

  fun minirig(address: String): Flow<Minirig?> = bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      adapter
        ?.getRemoteDevice(address)
        ?.toMinirig()
    }
    .distinctUntilChanged()
    .flowOn(context)
}
