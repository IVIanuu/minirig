/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.coroutines.*
import kotlinx.coroutines.*

@Provide class MinirigRemote(
  private val bluetoothManager: @SystemService BluetoothManager,
  private val context: IOContext,
  private val L: Logger
) {
  suspend fun <R> withMinirig(
    address: String,
    block: suspend (BluetoothSocket) -> R
  ): R? = withContext(context) {
    val device = bluetoothManager.adapter.bondedDevices.firstOrNull { it.address == address }!!

    if (!device.javaClass.getDeclaredMethod("isConnected").invoke(device).cast<Boolean>()) {
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
}
