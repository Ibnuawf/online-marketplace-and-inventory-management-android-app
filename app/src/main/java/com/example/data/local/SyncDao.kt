package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_operations ORDER BY createdAt ASC")
    suspend fun getPendingOperations(): List<SyncOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_operations WHERE id = :id")
    suspend fun deleteOperation(id: Int)

    @Query("DELETE FROM sync_operations WHERE productId = :productId")
    suspend fun deleteOperationsForProduct(productId: String)

    @Query("DELETE FROM sync_operations")
    suspend fun clearQueue()
}
