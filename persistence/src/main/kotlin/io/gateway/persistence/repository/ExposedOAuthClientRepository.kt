package io.gateway.persistence.repository

import io.gateway.domain.model.ClientId
import io.gateway.domain.model.GrantType
import io.gateway.domain.model.OAuthClient
import io.gateway.domain.model.TenantId
import io.gateway.domain.repository.OAuthClientRepository
import io.gateway.persistence.tables.OAuthClientsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

/** Exposed-backed [OAuthClientRepository]. Every query is scoped by tenant_id. */
class ExposedOAuthClientRepository : OAuthClientRepository {

    override suspend fun findById(tenantId: TenantId, id: ClientId): OAuthClient? = tx {
        OAuthClientsTable.selectAll()
            .where { (OAuthClientsTable.id eq id.value) and (OAuthClientsTable.tenantId eq tenantId.value.toString()) }
            .singleOrNull()?.toClient()
    }

    override suspend fun list(tenantId: TenantId): List<OAuthClient> = tx {
        OAuthClientsTable.selectAll()
            .where { OAuthClientsTable.tenantId eq tenantId.value.toString() }
            .map { it.toClient() }
    }

    override suspend fun insert(tenantId: TenantId, client: OAuthClient): OAuthClient = tx {
        OAuthClientsTable.insert { row ->
            row[id] = client.id.value
            row[OAuthClientsTable.tenantId] = tenantId.value.toString()
            row[name] = client.name
            row[public] = client.public
            row[secretHash] = client.secretHash
            row[redirectUris] = client.redirectUris.joinToString("\n")
            row[allowedScopes] = client.allowedScopes.joinToString(" ")
            row[grantTypes] = client.grantTypes.joinToString(" ") { it.wireName }
            row[requirePkce] = client.requirePkce
            row[requireConsent] = client.requireConsent
            row[requiredRoles] = client.requiredRoles.joinToString(" ")
            row[createdAt] = client.createdAt.toEpochMilliseconds()
        }
        client
    }

    override suspend fun update(tenantId: TenantId, client: OAuthClient): OAuthClient = tx {
        OAuthClientsTable.update({
            (OAuthClientsTable.id eq client.id.value) and (OAuthClientsTable.tenantId eq tenantId.value.toString())
        }) { row ->
            row[name] = client.name
            row[public] = client.public
            row[redirectUris] = client.redirectUris.joinToString("\n")
            row[allowedScopes] = client.allowedScopes.joinToString(" ")
            row[grantTypes] = client.grantTypes.joinToString(" ") { it.wireName }
            row[requirePkce] = client.requirePkce
            row[requireConsent] = client.requireConsent
            row[requiredRoles] = client.requiredRoles.joinToString(" ")
        }
        client
    }

    override suspend fun delete(tenantId: TenantId, id: ClientId) {
        tx {
            OAuthClientsTable.deleteWhere {
                (OAuthClientsTable.id eq id.value) and (OAuthClientsTable.tenantId eq tenantId.value.toString())
            }
        }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toClient(): OAuthClient = OAuthClient(
        id = ClientId(this[OAuthClientsTable.id]),
        name = this[OAuthClientsTable.name],
        public = this[OAuthClientsTable.public],
        secretHash = this[OAuthClientsTable.secretHash],
        redirectUris = this[OAuthClientsTable.redirectUris].lines().filter { it.isNotBlank() }.toSet(),
        allowedScopes = this[OAuthClientsTable.allowedScopes].split(" ").filter { it.isNotBlank() }.toSet(),
        grantTypes = this[OAuthClientsTable.grantTypes].split(" ")
            .mapNotNull { GrantType.fromWire(it) }
            .toSet(),
        requirePkce = this[OAuthClientsTable.requirePkce],
        requireConsent = this[OAuthClientsTable.requireConsent],
        requiredRoles = this[OAuthClientsTable.requiredRoles].split(" ").filter { it.isNotBlank() }.toSet(),
        createdAt = Instant.fromEpochMilliseconds(this[OAuthClientsTable.createdAt]),
    )
}
