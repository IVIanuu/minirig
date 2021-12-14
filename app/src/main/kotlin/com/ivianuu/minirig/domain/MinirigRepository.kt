/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.db.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.util.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.common.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.flow.*
import java.util.*

@Provide @Scoped<AppScope> class MinirigRepository(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val broadcastsFactory: BroadcastsFactory,
  private val context: IOContext,
  private val db: Db,
  private val L: Logger
) {
  val minirigs: Flow<List<Minirig>>
    get() = bondedDeviceChanges()
      .onStart<Any> { emit(Unit) }
      .map {
        bluetoothManager.adapter?.bondedDevices
          ?.filter { it.address.isMinirigAddress() }
          ?.map { it.toMinirig() }
          ?: emptyList()
      }

  val configs: Flow<List<MinirigConfig>>
    get() = db.selectAll()

  fun minirig(address: String): Flow<Minirig?> = bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      bluetoothManager.adapter?.bondedDevices
        ?.singleOrNull { it.address == address }
        ?.toMinirig()
    }

  fun config(id: String): Flow<MinirigConfig?> = db.selectById<MinirigConfig>(id)
    .map { config ->
      config ?: if (id.isMinirigAddress())
        MinirigConfig(id = id)
          .also { updateConfig(it) }
      else config
    }

  suspend fun updateConfig(config: MinirigConfig) = db.transaction {
    log { "update config $config" }
    db.insert(config, InsertConflictStrategy.REPLACE)
  }

  suspend fun deleteConfig(id: String) = db.transaction {
    db.deleteById<MinirigConfig>(id)
  }

  private fun bondedDeviceChanges() = broadcastsFactory(
    BluetoothAdapter.ACTION_STATE_CHANGED,
    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
    BluetoothDevice.ACTION_ACL_CONNECTED
  )

  private fun BluetoothDevice.toMinirig() = Minirig(address, name)
}

data class Minirig(val address: String, val name: String)

fun String.isMinirigAddress() = startsWith("00:12:6F")

val CLIENT_ID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")!!
