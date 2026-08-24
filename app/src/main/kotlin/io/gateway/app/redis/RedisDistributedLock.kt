package io.gateway.app.redis

import io.lettuce.core.SetArgs
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.time.Duration

/** Redis `SET key token NX PX ttl` lock, released only if we still own it. */
class RedisDistributedLock(private val redis: RedisCommands<String, String>) : DistributedLock {
    override suspend fun <T> withLock(key: String, ttl: Duration, block: suspend () -> T): T? {
        val token = UUID.randomUUID().toString()
        val acquired = withContext(Dispatchers.IO) {
            redis.set(key, token, SetArgs.Builder.nx().px(ttl.inWholeMilliseconds)) != null
        }
        if (!acquired) return null
        try {
            return block()
        } finally {
            withContext(Dispatchers.IO) {
                if (redis.get(key) == token) redis.del(key)
            }
        }
    }
}
