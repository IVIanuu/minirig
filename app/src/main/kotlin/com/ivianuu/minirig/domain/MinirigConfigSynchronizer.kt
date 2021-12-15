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
  context: IOContext,
  repository: MinirigRepository,
  R: MinirigRemote,
  L: Logger,
  T: ToastContext
) = ScopeWorker<AppScope> {
  repository.minirigs
    .flatMapLatest { minirigs ->
      combine(
        minirigs
          .map { minirig ->
            repository.config(minirig.address)
              .map { minirig to it }
          }
      ) { it.toList() }
    }
    .collectLatest { configs ->
      withContext(context) {
        configs
          .filter { it.second?.id?.isMinirigAddress() == true }
          .parForEach { (minirig, config) ->
            log { "write config ${minirig.readableName()}" }
            catch {
              applyConfig(config!!)
            }.onFailure {
              log { "failed to apply config to ${minirig.readableName()} -> ${it.asLog()}" }
              showToast("Could not apply config to ${minirig.readableName()}")
            }
          }
      }
    }
}

private suspend fun applyConfig(
  config: MinirigConfig,
  @Inject L: Logger,
  remote: MinirigRemote
) {
  remote.withMinirig(config.id) { socket ->
    log { "${config.id} apply config $config" }

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
        log { "${socket.remoteDevice.readableName()} update $finalKey -> $finalValue" }
        socket.outputStream.write("q p $finalKey $finalValue".toByteArray())
        // the minirig cannot keep with our speed to debounce each write
        delay(100)
      }
    }

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

    updateConfigIfNeeded(1, (config.band1 * 99).toInt())
    updateConfigIfNeeded(2, (config.band2 * 99).toInt())
    updateConfigIfNeeded(3, (config.band3 * 99).toInt())
    updateConfigIfNeeded(4, (config.band4 * 99).toInt())
    updateConfigIfNeeded(5, (config.band5 * 99).toInt())

    updateConfigIfNeeded(
      14,
      ((1f - config.channel) * 99).toInt()
    )

    updateConfigIfNeeded(
      15,
      ((1f - config.auxChannel) * 99).toInt()
    )

    updateConfigIfNeeded(
      7,
      // everything above 7 sounds not healthy
      (7 * config.bassGain).toInt()
    )

    updateConfigIfNeeded(
      12,
      if (config.loud) 1 else 0
    )
  }
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
        log { "${remoteDevice.readableName()} received $curr" }
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
        log { "${remoteDevice.readableName()} stats $curr" }
      }
    }
  }
}

fun BluetoothDevice.readableName() = "[$name ~ $address]"

private fun Minirig.readableName() = "[$name ~ $address]"
