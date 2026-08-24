package io.gateway.oidc

import com.nimbusds.jose.jwk.RSAKey
import io.gateway.common.RandomTokens
import io.gateway.common.SecretCipher
import io.gateway.domain.model.SigningKeyRecord
import io.gateway.domain.model.TenantId
import io.gateway.domain.repository.SigningKeyRepository
import io.gateway.domain.time.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Persistent, rotatable RS256 signing keys — one key set per tenant. Keys survive
 * restarts (private key encrypted at rest) so previously issued tokens keep verifying.
 * On rotation the old key is retired but stays in JWKS for [retention] so its tokens
 * still verify. Each tenant's keys are initialized eagerly (startup + on provisioning),
 * so reads ([current], [publicKeyFor]) are non-suspending.
 */
class SigningKeyManager(
    private val repo: SigningKeyRepository,
    private val cipher: SecretCipher,
    private val clock: Clock,
    private val retention: Duration = DEFAULT_RETENTION,
) {
    private data class TenantKeys(
        val activeKey: RsaSigningKey,
        val publicByKid: Map<String, RSAKey>,
        val activeCreatedAt: Instant,
    )

    private val states = ConcurrentHashMap<String, TenantKeys>()

    /** Load (or generate) a tenant's key set. Idempotent — safe to call repeatedly. */
    suspend fun initialize(tenantId: TenantId) {
        if (states.containsKey(tenantId.key())) return
        if (repo.all(tenantId).none { it.active }) generateAndPersist(tenantId)
        reload(tenantId)
    }

    /** Retire the tenant's active key(s) and generate a fresh one. */
    suspend fun rotate(tenantId: TenantId) {
        val cutoff = clock.now().plus(retention)
        repo.all(tenantId).filter { it.active }.forEach { repo.retire(tenantId, it.kid, cutoff) }
        generateAndPersist(tenantId)
        reload(tenantId)
    }

    fun current(tenantId: TenantId): RsaSigningKey = state(tenantId).activeKey

    /** Creation time of the tenant's active key, for rotation-age checks. Null before init. */
    fun activeKeyCreatedAt(tenantId: TenantId): Instant? = states[tenantId.key()]?.activeCreatedAt

    fun publicJwks(tenantId: TenantId): List<RSAKey> = state(tenantId).publicByKid.values.toList()

    fun publicKeyFor(tenantId: TenantId, kid: String?): RSAKey? =
        kid?.let { states[tenantId.key()]?.publicByKid?.get(it) }

    private fun state(tenantId: TenantId): TenantKeys =
        states[tenantId.key()] ?: error("Signing keys not initialized for tenant $tenantId.")

    private fun TenantId.key(): String = value.toString()

    private suspend fun generateAndPersist(tenantId: TenantId) {
        val key = RsaSigningKey.generate("gw-" + RandomTokens.urlSafe(KID_BYTES))
        repo.insert(
            tenantId,
            SigningKeyRecord(
                kid = key.keyId,
                algorithm = "RS256",
                publicJwk = key.publicJwk().toJSONString(),
                privateKeyEnc = cipher.encrypt(key.privateJwkJson()),
                active = true,
                createdAt = clock.now(),
                expiresAt = null,
            ),
        )
    }

    private suspend fun reload(tenantId: TenantId) {
        val now = clock.now()
        val publishable = repo.all(tenantId).filter { it.isPublishable(now) }
        val decoded = publishable.map { it to RsaSigningKey.fromJwkJson(cipher.decrypt(it.privateKeyEnc)) }
        val active = decoded.filter { (record, _) -> record.active }
            .maxByOrNull { (record, _) -> record.createdAt }
            ?: error("No active signing key after reload for tenant $tenantId.")
        states[tenantId.key()] = TenantKeys(
            activeKey = active.second,
            publicByKid = decoded.associate { (_, key) -> key.keyId to key.publicJwk() },
            activeCreatedAt = active.first.createdAt,
        )
    }

    private companion object {
        val DEFAULT_RETENTION = 7.days
        const val KID_BYTES = 8
    }
}
