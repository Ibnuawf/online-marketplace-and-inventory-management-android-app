package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class SyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    CONFLICT
}

@Entity(tableName = "products")
@JsonClass(generateAdapter = true)
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val quantity: Int,
    val imageUrl: String?,
    val createdBy: String?,
    val version: Int,
    val updatedAt: Long, // timestamp in ms
    val syncStatus: SyncStatus
)
