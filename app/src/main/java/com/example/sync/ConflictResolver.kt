package com.example.sync

import com.example.data.local.ProductEntity
import com.example.data.local.SyncStatus
import com.example.data.remote.RemoteProduct
import java.time.Instant

object ConflictResolver {
    fun parseIsoStringToMillis(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            try {
                isoString.toLong()
            } catch (ex: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    fun parseMillisToIsoString(millis: Long): String {
        return try {
            Instant.ofEpochMilli(millis).toString()
        } catch (e: Exception) {
            Instant.now().toString()
        }
    }

    fun resolveServerWins(local: ProductEntity, server: RemoteProduct): ProductEntity {
        return ProductEntity(
            id = server.id,
            name = server.name,
            description = server.description,
            price = server.price,
            quantity = server.quantity,
            imageUrl = server.imageUrl,
            createdBy = server.createdBy,
            version = server.version,
            updatedAt = parseIsoStringToMillis(server.updatedAt),
            syncStatus = SyncStatus.SYNCED
        )
    }

    fun fromRemote(server: RemoteProduct): ProductEntity {
        return ProductEntity(
            id = server.id,
            name = server.name,
            description = server.description,
            price = server.price,
            quantity = server.quantity,
            imageUrl = server.imageUrl,
            createdBy = server.createdBy,
            version = server.version,
            updatedAt = parseIsoStringToMillis(server.updatedAt),
            syncStatus = SyncStatus.SYNCED
        )
    }

    fun toRemote(entity: ProductEntity): RemoteProduct {
        return RemoteProduct(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            price = entity.price,
            quantity = entity.quantity,
            imageUrl = entity.imageUrl,
            createdBy = entity.createdBy,
            version = entity.version,
            updatedAt = parseMillisToIsoString(entity.updatedAt)
        )
    }
}
