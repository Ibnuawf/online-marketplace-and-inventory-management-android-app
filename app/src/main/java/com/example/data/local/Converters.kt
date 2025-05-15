package com.example.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return try {
            SyncStatus.valueOf(value)
        } catch (e: Exception) {
            SyncStatus.SYNCED
        }
    }

    @TypeConverter
    fun fromOperationType(type: OperationType): String {
        return type.name
    }

    @TypeConverter
    fun toOperationType(value: String): OperationType {
        return try {
            OperationType.valueOf(value)
        } catch (e: Exception) {
            OperationType.CREATE
        }
    }
}
