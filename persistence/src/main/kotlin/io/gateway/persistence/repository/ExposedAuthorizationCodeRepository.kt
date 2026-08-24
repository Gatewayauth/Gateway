package io.gateway.persistence.repository

import io.gateway.domain.model.AuthorizationGrant
import io.gateway.domain.model.ClientId
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.UserId
import io.gateway.domain.repository.AuthorizationCodeRepository
import io.gateway.persistence.tables.AuthorizationCodesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** Exposed-backed [AuthorizationCodeRepository] with atomic single-use consumption. */
class ExposedAuthorizationCodeRepository : AuthorizationCodeRepository {

    override suspend fun insert(tenantId: TenantId, grant: AuthorizationGrant) {
        tx {
            AuthorizationCodesTable.insert { row ->
                row[codeHash] = grant.codeHash
                row[AuthorizationCodesTable.tenantId] = tenantId.value.toString()
                row[clientId] = grant.clientId.value
                row[userId] = grant.userId.value.toString()
                row[redirectUri] = grant.redirectUri
                row[scopes] = grant.scopes.joinToString(" ")
                row[nonce] = grant.nonce
                row[codeChallenge] = grant.codeChallenge
                row[codeChallengeMethod] = grant.codeChallengeMethod
                row[authTime] = grant.authTime.toEpochMilliseconds()
                row[expiresAt] = grant.expiresAt.toEpochMilliseconds()
                row[consumedAt] = grant.consumedAt?.toEpochMilliseconds()
            }
        }
    }

    override suspend fun consume(tenantId: TenantId, codeHash: String, now: Instant): AuthorizationGrant? = tx {
        val tid = tenantId.value.toString()
        val row = AuthorizationCodesTable.selectAll()
            .where { (AuthorizationCodesTable.codeHash eq codeHash) and (AuthorizationCodesTable.tenantId eq tid) }
            .singleOrNull() ?: return@tx null

        // Claim atomically: only one concurrent caller can flip an unused, unexpired
        // code to consumed. A SELECT-then-UPDATE would let two callers both win.
        val nowMs = now.toEpochMilliseconds()
        val claimed = AuthorizationCodesTable.update(
            {
                (AuthorizationCodesTable.codeHash eq codeHash) and
                    (AuthorizationCodesTable.tenantId eq tid) and
                    AuthorizationCodesTable.consumedAt.isNull() and
                    (AuthorizationCodesTable.expiresAt greater nowMs)
            },
        ) {
            it[consumedAt] = nowMs
        }
        if (claimed == 0) return@tx null
        row.toGrant()
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toGrant(): AuthorizationGrant = AuthorizationGrant(
        codeHash = this[AuthorizationCodesTable.codeHash],
        clientId = ClientId(this[AuthorizationCodesTable.clientId]),
        userId = UserId.parse(this[AuthorizationCodesTable.userId]),
        redirectUri = this[AuthorizationCodesTable.redirectUri],
        scopes = this[AuthorizationCodesTable.scopes].split(" ").filter { it.isNotBlank() }.toSet(),
        nonce = this[AuthorizationCodesTable.nonce],
        codeChallenge = this[AuthorizationCodesTable.codeChallenge],
        codeChallengeMethod = this[AuthorizationCodesTable.codeChallengeMethod],
        authTime = Instant.fromEpochMilliseconds(this[AuthorizationCodesTable.authTime]),
        expiresAt = Instant.fromEpochMilliseconds(this[AuthorizationCodesTable.expiresAt]),
        consumedAt = this[AuthorizationCodesTable.consumedAt]?.let { Instant.fromEpochMilliseconds(it) },
    )
}
