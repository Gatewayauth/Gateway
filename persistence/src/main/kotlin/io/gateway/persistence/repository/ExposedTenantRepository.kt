package io.gateway.persistence.repository

import io.gateway.domain.model.Tenant
import io.gateway.domain.model.TenantId
import io.gateway.domain.model.TenantStatus
import io.gateway.domain.repository.TenantRepository
import io.gateway.persistence.tables.TenantsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import java.util.UUID

/** Exposed-backed [TenantRepository]. */
class ExposedTenantRepository : TenantRepository {

    override suspend fun findBySlug(slug: String): Tenant? = tx {
        TenantsTable.selectAll().where { TenantsTable.slug eq slug }.singleOrNull()?.toTenant()
    }

    override suspend fun findById(id: TenantId): Tenant? = tx {
        TenantsTable.selectAll().where { TenantsTable.id eq id.value.toString() }.singleOrNull()?.toTenant()
    }

    override suspend fun insert(tenant: Tenant): Tenant = tx {
        TenantsTable.insert { row ->
            row[id] = tenant.id.value.toString()
            row[slug] = tenant.slug
            row[name] = tenant.name
            row[status] = tenant.status.name
            row[createdAt] = tenant.createdAt.toEpochMilliseconds()
        }
        tenant
    }

    override suspend fun list(): List<Tenant> = tx {
        TenantsTable.selectAll().orderBy(TenantsTable.createdAt to SortOrder.ASC).map { it.toTenant() }
    }

    private suspend fun <T> tx(block: () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toTenant(): Tenant = Tenant(
        id = TenantId(UUID.fromString(this[TenantsTable.id])),
        slug = this[TenantsTable.slug],
        name = this[TenantsTable.name],
        status = TenantStatus.valueOf(this[TenantsTable.status]),
        createdAt = Instant.fromEpochMilliseconds(this[TenantsTable.createdAt]),
    )
}
