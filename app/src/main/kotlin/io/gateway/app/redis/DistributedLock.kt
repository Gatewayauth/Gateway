package io.gateway.app.redis

import kotlin.time.Duration

/**
 * Best-effort mutual exclusion across instances. [withLock] runs [block] only if it
 * acquires [key] for [ttl], returning its result — or null if another holder has it.
 */
interface DistributedLock {
    suspend fun <T> withLock(key: String, ttl: Duration, block: suspend () -> T): T?
}
