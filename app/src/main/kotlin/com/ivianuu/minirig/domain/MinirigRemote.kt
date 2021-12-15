/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.db.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.util.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

@Provide class MinirigRemote(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val broadcastsFactory: BroadcastsFactory,
  private val context: IOContext,
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
      .distinctUntilChanged()

  fun minirig(address: String): Flow<Minirig?> = bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      bluetoothManager.adapter
        ?.getRemoteDevice(address)
        ?.toMinirig()
    }

  fun isConnected(address: String) = bondedDeviceChanges()
    .onStart<Any> { emit(Unit) }
    .map {
      val device = bluetoothManager.adapter.getRemoteDevice(address)
      device.javaClass.getDeclaredMethod("isConnected").invoke(device).cast<Boolean>()
    }
    .distinctUntilChanged()

  suspend fun <R> withMinirig(
    address: String,
    block: suspend (BluetoothSocket) -> R
  ): R? = withContext(context) {
    val device = bluetoothManager.adapter.bondedDevices.firstOrNull { it.address == address }!!

    if (!isConnected(address).first()) {
      log { "skip not connected device ${device.readableName()}" }
      return@withContext null
    }

    val socket = device.createRfcommSocketToServiceRecord(CLIENT_ID)
      .also { socket ->
        val connectComplete = CompletableDeferred<Unit>()
        par(
          {
            log { "connect ${device.readableName()} on ${Thread.currentThread().name}" }
            try {
              var attempt = 0
              while (attempt < 5) {
                try {
                  socket.connect()
                  if (socket.isConnected) break
                } catch (e: Throwable) {
                  e.nonFatalOrThrow()
                  attempt++
                  delay(1000)
                }
              }
            } finally {
              connectComplete.complete(Unit)
            }
          },
          {
            onCancel(
              block = { connectComplete.await() },
              onCancel = {
                log { "cancel connect ${device.readableName()} on ${Thread.currentThread().name}" }
                catch { socket.close() }
              }
            )
          }
        )
      }

    if (!socket.isConnected) {
      log { "could not connect to ${device.readableName()}" }
      return@withContext null
    }

    log { "connected to ${device.readableName()}" }

    return@withContext guarantee(
      block = { block(socket) },
      finalizer = {
        log { "disconnect from ${device.readableName()}" }
        catch { socket.close() }
      }
    )
  }

  private fun bondedDeviceChanges() = broadcastsFactory(
    BluetoothAdapter.ACTION_STATE_CHANGED,
    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
    BluetoothDevice.ACTION_ACL_CONNECTED,
    BluetoothDevice.ACTION_ACL_DISCONNECTED
  )
}

val CLIENT_ID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")!!
