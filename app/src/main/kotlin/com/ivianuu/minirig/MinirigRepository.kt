/*
 * Copyright 2022 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig

import android.bluetooth.BluetoothManager
import com.ivianuu.essentials.coroutines.CoroutineContexts
import com.ivianuu.essentials.permission.PermissionManager
import com.ivianuu.injekt.Provide
import com.ivianuu.injekt.android.SystemService
import com.ivianuu.injekt.common.typeKeyOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@Provide class MinirigRepository(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val coroutineContexts: CoroutineContexts,
  permissionManager: PermissionManager,
  private val remote: MinirigRemote
) {
  val minirigs: Flow<List<Minirig>> = permissionManager.permissionState(listOf(typeKeyOf<MinirigBluetoothConnectPermission>()))
    .flatMapLatest {
      if (!it) flowOf(emptyList())
      else remote.bondedDeviceChanges()
        .onStart<Any> { emit(Unit) }
        .map {
          bluetoothManager.adapter?.bondedDevices
            ?.filter { it.isMinirig() }
            ?.map { it.toMinirig() }
            ?: emptyList()
        }
        .flowOn(coroutineContexts.io)
    }
    .distinctUntilChanged()

  fun minirig(address: String): Flow<Minirig?> = remote.bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      bluetoothManager.adapter
        ?.getRemoteDevice(address)
        ?.toMinirig()
    }
    .distinctUntilChanged()
    .flowOn(coroutineContexts.io)
}
