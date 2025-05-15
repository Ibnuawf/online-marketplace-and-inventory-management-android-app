package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE
}

@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: String,
    val operationType: OperationType,
    val createdAt: Long = System.currentTimeMillis()
)
