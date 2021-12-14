/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig.domain

import android.bluetooth.*
import android.bluetooth.le.*
import com.ivianuu.essentials.*
import com.ivianuu.essentials.app.*
import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.essentials.state.*
import com.ivianuu.essentials.time.*
import com.ivianuu.essentials.util.*
import com.ivianuu.injekt.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.coroutines.*
import com.ivianuu.minirig.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.charset.*
import kotlin.coroutines.*

@Provide fun minirigConfigSynchronizer(
  bluetoothManager: @SystemService BluetoothManager,
  context: IOContext,
  repository: MinirigRepository,
  L: Logger
) = ScopeWorker<AppScope> {
  repository.minirigs
    .flatMapLatest { minirigs ->
      combine(
        minirigs
          .map { repository.config(it.address) }
      ) { it.toList() }
    }
    .collect { configs ->
      withContext(context) {
        configs
          .filter { it?.id?.isMinirigAddress() == true }
          .parForEach { config ->
            config!!
            log { "write config ${config.id}" }
            catch {
              applyConfig(config)
            }.onFailure { log { "failed to apply config to ${config.id} -> ${it.asLog()}" } }
          }
      }
    }
}

private suspend fun applyConfig(
  config: MinirigConfig,
  @Inject bluetoothManager: @SystemService BluetoothManager,
  L: Logger
) {
  val device = bluetoothManager.adapter.bondedDevices.firstOrNull {
    it.address == config.id
  }!!
  val socket = device.createRfcommSocketToServiceRecord(CLIENT_ID)
    .also { socket ->
      log { "connect to ${config.id}" }
      socket.connect()
      log { "connected to ${config.id} ${socket.isConnected}" }
    }

  guarantee(
    block = {
      val currentConfig = socket.readMinirigConfig()

      log { "${config.id} current config $currentConfig" }

      suspend fun updateConfigIfNeeded(key: Int, value: Int) {
        // format key and value to match the minirig format
        var finalKey = key.toString()
        if (finalKey.length == 1)
          finalKey = "0$finalKey"

        var finalValue = value.toString()
        if (finalValue.length == 1)
          finalValue = "0$finalValue"

        // only write if the value has changed
        if (currentConfig[key] != value) {
          log { "${config.id} update $finalKey -> $finalValue" }
          socket.outputStream.write("q p $finalKey $finalValue".toByteArray())
          // the minirig cannot keep with out speed to debounce each write
          delay(500)
        }
      }

      updateConfigIfNeeded(1, (config.band1 * 99).toInt())
      updateConfigIfNeeded(2, (config.band2 * 99).toInt())
      updateConfigIfNeeded(3, (config.band3 * 99).toInt())
      updateConfigIfNeeded(4, (config.band4 * 99).toInt())
      updateConfigIfNeeded(5, (config.band5 * 99).toInt())
      updateConfigIfNeeded(
        8,
        // > 30 means mutes the minirig
        if (config.gain == 0f) 31
        // minirig value range is 0..30 and 30 means lowest gain
        else (30 * (1f - config.gain)).toInt()
      )

      updateConfigIfNeeded(
        9,
        // > 10 means mutes the aux device
        if (config.auxGain == 0f) 11
        // minirig value range is 0..10 and 10 means highest gain
        else (10 * config.auxGain).toInt()
      )

      updateConfigIfNeeded(
        7,
        // everything above 7 sounds not healthy
        (7 * config.bassGain).toInt()
      )

      updateConfigIfNeeded(
        14,
        ((1f - config.channel) * 99).toInt()
      )
    },
    finalizer = {
      log { "disconnect from ${config.id}" }
      catch { socket.close() }
    }
  )
}

private suspend fun BluetoothSocket.readMinirigConfig(@Inject L: Logger): Map<Int, Int> {
  // sending this message triggers the state output
  outputStream.write("q p 00 50".toByteArray())

  withTimeoutOrNull(5.seconds) {
    while (isActive && isConnected) {
      if (inputStream.available() > 0) {
        val arr = ByteArray(inputStream.available())
        inputStream.read(arr)
        val curr = arr.toString(StandardCharsets.UTF_8)
        log { "${remoteDevice.address} received $curr" }
        if (curr.startsWith("q")) {
          return@withTimeoutOrNull curr.removePrefix("q ")
            .split(" ")
            .withIndex()
            .associateBy { it.index + 1 }
            .mapValues { it.value.value.toInt() }
        }
      }
    }

    awaitCancellation()
  }?.let { return it }

  throw IllegalStateException("Could not read minirig config for ${remoteDevice.name}")
}

private suspend fun BluetoothSocket.readMinirigStatus(@Inject L: Logger) {
  // sending this message triggers the state output
  outputStream.write("xGET_STATUS".toByteArray())
  outputStream.write("BGET_BATTERY".toByteArray())

  withTimeoutOrNull(5.seconds) {
    while (isActive && isConnected) {
      if (inputStream.available() > 0) {
        val arr = ByteArray(inputStream.available())
        inputStream.read(arr)
        val curr = arr.toString(StandardCharsets.UTF_8)
        log { "${remoteDevice.address} stats $curr" }
      }
    }
  }
}
