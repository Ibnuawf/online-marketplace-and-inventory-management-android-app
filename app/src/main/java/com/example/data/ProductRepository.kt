package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.OperationType
import com.example.data.local.ProductEntity
import com.example.data.local.SyncOperationEntity
import com.example.data.local.SyncStatus
import com.example.sync.SyncManager
import com.example.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class ProductRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val productDao = database.productDao()
    private val syncDao = database.syncDao()
    private val syncManager = SyncManager(context)
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    suspend fun getProductById(id: String): ProductEntity? {
        return productDao.getProductById(id)
    }

    suspend fun createProduct(name: String, description: String, price: Double, quantity: Int, imageUrl: String? = null): String {
        val productId = UUID.randomUUID().toString()
        val product = ProductEntity(
            id = productId,
            name = name,
            description = description,
            price = price,
            quantity = quantity,
            imageUrl = imageUrl,
            createdBy = null,
            version = 1,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_CREATE
        )

        productDao.insertProduct(product)

        syncDao.insertOperation(
            SyncOperationEntity(
                productId = productId,
                operationType = OperationType.CREATE
            )
        )

        triggerSync()

        return productId
    }

    suspend fun updateProduct(
        id: String,
        name: String,
        description: String,
        price: Double,
        quantity: Int,
        imageUrl: String? = null,
        currentVersion: Int
    ) {
        val product = ProductEntity(
            id = id,
            name = name,
            description = description,
            price = price,
            quantity = quantity,
            imageUrl = imageUrl,
            createdBy = null,
            version = currentVersion,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_UPDATE
        )

        productDao.insertProduct(product)

        syncDao.insertOperation(
            SyncOperationEntity(
                productId = id,
                operationType = OperationType.UPDATE
            )
        )

        triggerSync()
    }

    suspend fun deleteProduct(id: String) {
        val existing = productDao.getProductById(id) ?: return

        if (existing.syncStatus == SyncStatus.PENDING_CREATE) {
            productDao.deleteProductById(id)
            syncDao.deleteOperationsForProduct(id)
        } else {
            val deletedProduct = existing.copy(
                syncStatus = SyncStatus.PENDING_DELETE,
                updatedAt = System.currentTimeMillis()
            )
            productDao.insertProduct(deletedProduct)

            syncDao.insertOperation(
                SyncOperationEntity(
                    productId = id,
                    operationType = OperationType.DELETE
                )
            )
        }

        triggerSync()
    }

    fun triggerSync() {
        repositoryScope.launch {
            try {
                val success = syncManager.sync()
                Log.d("ProductRepository", "Immediate sync triggered. Success: $success")
            } catch (e: Exception) {
                Log.e("ProductRepository", "Immediate sync failed, scheduling via WorkManager.", e)
            }
            SyncWorker.enqueue(context)
        }
    }
}
