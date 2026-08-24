package io.gateway.app

import io.gateway.app.redis.DistributedLock
import io.gateway.audit.AuditEventType
import io.gateway.audit.AuditLogger
import io.gateway.domain.model.TenantId
import io.gateway.domain.repository.TenantRepository
import io.gateway.domain.time.Clock
import io.gateway.oidc.SigningKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Rotates each tenant's JWT signing key once it reaches [rotationInterval] in age.
 * Runs a lightweight periodic check on the application scope; disabled when the
 * interval is zero or negative. A distributed lock (keyed per tenant) ensures only
 * one instance rotates a given tenant.
 */
class KeyRotationScheduler(
    private val keys: SigningKeyManager,
    private val tenants: TenantRepository,
    private val audit: AuditLogger,
    private val clock: Clock,
    private val rotationInterval: Duration,
    private val lock: DistributedLock,
) {
    private val log = LoggerFactory.getLogger("io.gateway.KeyRotationScheduler")

    /** Pure age check for one tenant — testable without timers. */
    fun isDue(now: Instant, tenantId: TenantId): Boolean {
        val createdAt = keys.activeKeyCreatedAt(tenantId) ?: return false
        return now - createdAt >= rotationInterval
    }

    fun start(scope: CoroutineScope): Job? {
        if (rotationInterval <= Duration.ZERO) return null
        val checkInterval = minOf(rotationInterval, MAX_CHECK_INTERVAL)
        log.info("Signing-key auto-rotation enabled (interval={}).", rotationInterval)
        return scope.launch {
            while (isActive) {
                delay(checkInterval)
                rotateDueTenants()
            }
        }
    }

    private suspend fun rotateDueTenants() {
        val now = clock.now()
        tenants.list().forEach { tenant ->
            if (isDue(now, tenant.id)) rotate(tenant.id)
        }
    }

    private suspend fun rotate(tenantId: TenantId) {
        // Only one instance should rotate a given tenant; others skip when the lock is held.
        val rotated = lock.withLock("$ROTATION_LOCK_PREFIX$tenantId", LOCK_TTL) {
            keys.rotate(tenantId)
            audit.record(tenantId, AuditEventType.SIGNING_KEY_ROTATED, null, null, null, "auto")
            log.info("Signing key auto-rotated for tenant {}.", tenantId)
        }
        if (rotated == null) log.debug("Rotation skipped for {}; another instance holds the lock.", tenantId)
    }

    private companion object {
        val MAX_CHECK_INTERVAL = 1.hours
        val LOCK_TTL = 30.seconds
        const val ROTATION_LOCK_PREFIX = "gw:key-rotation:"
    }
}
