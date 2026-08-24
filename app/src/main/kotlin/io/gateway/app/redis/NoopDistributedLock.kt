package io.gateway.app.redis

import kotlin.time.Duration

/** Single-instance: always "acquires" and runs the block. */
class NoopDistributedLock : DistributedLock {
    override suspend fun <T> withLock(key: String, ttl: Duration, block: suspend () -> T): T? = block()
}
