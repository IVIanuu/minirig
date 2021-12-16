/*
 * Copyright 2021 Manuel Wrage. Use of this source code is governed by the Apache 2.0 license.
 */

package com.ivianuu.minirig

import com.ivianuu.essentials.coroutines.*
import com.ivianuu.essentials.logging.*
import com.ivianuu.injekt.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

private val job = GlobalScope.launch {
  while (coroutineContext.isActive) {
    println("jobs: open jobs $openJobs")
    delay(3000)
  }
}

private val lastId = Atomic(0)
private val openJobs = mutableMapOf<String, MutableSet<Int>>()
private val openJobsLock = Mutex()

suspend fun <R> runJob(
  name: String,
  @Inject L: Logger,
  block: suspend () -> R
): R {
  val id = lastId.getAndUpdate { it.inc() }
  return try {
    openJobsLock.withLock {
      openJobs.getOrPut(name) { mutableSetOf() }.add(id)
    }
    log { "jobs $name $id start" }
    block()
  } finally {
    log { "jobs $name $id stop" }
    openJobsLock.withLock {
      val jobs = openJobs[name]!!
      jobs.remove(id)
      if (jobs.isEmpty())
        openJobs.remove(name)
    }
  }
}
