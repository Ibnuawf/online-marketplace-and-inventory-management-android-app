package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.OperationType
import com.example.data.local.ProductDao
import com.example.data.local.ProductEntity
import com.example.data.local.SyncDao
import com.example.data.local.SyncOperationEntity
import com.example.data.local.SyncStatus
import com.example.data.remote.RemoteProduct
import com.example.sync.ConflictResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var productDao: ProductDao
    private lateinit var syncDao: SyncDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productDao = db.productDao()
        syncDao = db.syncDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context matches updated app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Inventory Marketplace", appName)
    }

    @Test
    fun `conflict resolution - server wins strategy replaces local values`() {
        // Arrange
        val localProduct = ProductEntity(
            id = "product_123",
            name = "Local Name",
            description = "Local Description",
            price = 10.0,
            quantity = 5,
            imageUrl = null,
            createdBy = "user_client",
            version = 1,
            updatedAt = 1000L,
            syncStatus = SyncStatus.PENDING_UPDATE
        )

        val serverProduct = RemoteProduct(
            id = "product_123",
            name = "Server Name",
            description = "Server Description",
            price = 15.0,
            quantity = 20,
            imageUrl = "http://example.com/image.png",
            createdBy = "user_server",
            version = 2,
            updatedAt = "2026-07-11T14:24:05Z"
        )

        // Act
        val resolved = ConflictResolver.resolveServerWins(localProduct, serverProduct)

        // Assert
        assertEquals("product_123", resolved.id)
        assertEquals("Server Name", resolved.name)
        assertEquals("Server Description", resolved.description)
        assertEquals(15.0, resolved.price, 0.0)
        assertEquals(20, resolved.quantity)
        assertEquals("http://example.com/image.png", resolved.imageUrl)
        assertEquals("user_server", resolved.createdBy)
        assertEquals(2, resolved.version)
        assertEquals(SyncStatus.SYNCED, resolved.syncStatus)
    }

    @Test
    fun `map remote product to local entity`() {
        // Arrange
        val remote = RemoteProduct(
            id = "remote_999",
            name = "Remote Tablet",
            description = "Octa-core 10-inch",
            price = 299.99,
            quantity = 50,
            imageUrl = null,
            createdBy = "admin",
            version = 3,
            updatedAt = "1773094800000" // Milliseconds as string
        )

        // Act
        val entity = ConflictResolver.fromRemote(remote)

        // Assert
        assertNotNull(entity)
        assertEquals("remote_999", entity.id)
        assertEquals("Remote Tablet", entity.name)
        assertEquals(299.99, entity.price, 0.0)
        assertEquals(50, entity.quantity)
        assertEquals(3, entity.version)
        assertEquals(SyncStatus.SYNCED, entity.syncStatus)
    }

    @Test
    fun `test offline product creation`() = runBlocking {
        // Arrange
        val product = ProductEntity(
            id = "test_product_1",
            name = "Offline Hammer",
            description = "Heavy duty hammer",
            price = 12.99,
            quantity = 10,
            imageUrl = null,
            createdBy = null,
            version = 1,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_CREATE
        )
        val syncOp = SyncOperationEntity(
            productId = "test_product_1",
            operationType = OperationType.CREATE
        )

        // Act - Simulate offline write
        productDao.insertProduct(product)
        syncDao.insertOperation(syncOp)

        // Assert - Product still exists and is pending create
        val fetchedProduct = productDao.getProductById("test_product_1")
        assertNotNull(fetchedProduct)
        assertEquals("Offline Hammer", fetchedProduct!!.name)
        assertEquals(SyncStatus.PENDING_CREATE, fetchedProduct.syncStatus)

        val pendingOps = syncDao.getPendingOperations()
        assertEquals(1, pendingOps.size)
        assertEquals("test_product_1", pendingOps[0].productId)
        assertEquals(OperationType.CREATE, pendingOps[0].operationType)
    }

    @Test
    fun `test automatic sync`() = runBlocking {
        // Arrange - Setup a pending local product
        val product = ProductEntity(
            id = "test_product_2",
            name = "Screwdriver",
            description = "Magnetic tip",
            price = 5.99,
            quantity = 25,
            imageUrl = null,
            createdBy = null,
            version = 1,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_CREATE
        )
        productDao.insertProduct(product)
        syncDao.insertOperation(SyncOperationEntity(productId = "test_product_2", operationType = OperationType.CREATE))

        // Act - Simulate automatic synchronization transitioning status to SYNCED
        val syncedProduct = productDao.getProductById("test_product_2")!!.copy(syncStatus = SyncStatus.SYNCED)
        productDao.insertProduct(syncedProduct)
        syncDao.deleteOperationsForProduct("test_product_2")

        // Assert
        val updatedProduct = productDao.getProductById("test_product_2")
        assertNotNull(updatedProduct)
        assertEquals(SyncStatus.SYNCED, updatedProduct!!.syncStatus)
        assertEquals(0, syncDao.getPendingOperations().size)
    }

    @Test
    fun `test update conflict resolution`() = runBlocking {
        // Arrange - Phone has version 1
        val localProduct = ProductEntity(
            id = "test_conflict_product",
            name = "Local Router",
            description = "Wi-Fi 6 Router",
            price = 89.99,
            quantity = 15,
            imageUrl = null,
            createdBy = "user",
            version = 1,
            updatedAt = 1000L,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        productDao.insertProduct(localProduct)

        // Server has version 2
        val serverProduct = RemoteProduct(
            id = "test_conflict_product",
            name = "Server Router",
            description = "Wi-Fi 6 Router Premium",
            price = 99.99,
            quantity = 12,
            imageUrl = "http://example.com/router.png",
            createdBy = "admin",
            version = 2,
            updatedAt = "2000"
        )

        // Act - Detect conflict and resolve using Server Wins Strategy
        val localInDb = productDao.getProductById("test_conflict_product")
        assertNotNull(localInDb)
        
        // Conflict resolver keeps server data, replaces local, marks as SYNCED
        val resolved = ConflictResolver.resolveServerWins(localInDb!!, serverProduct)
        productDao.insertProduct(resolved)
        syncDao.deleteOperationsForProduct(resolved.id)

        // Assert
        val dbProduct = productDao.getProductById("test_conflict_product")
        assertNotNull(dbProduct)
        assertEquals("Server Router", dbProduct!!.name)
        assertEquals("Wi-Fi 6 Router Premium", dbProduct.description)
        assertEquals(99.99, dbProduct.price, 0.0)
        assertEquals(12, dbProduct.quantity)
        assertEquals(2, dbProduct.version)
        assertEquals(SyncStatus.SYNCED, dbProduct.syncStatus)
    }

    @Test
    fun `test offline delete`() = runBlocking {
        // Arrange - We have an existing synced product in DB
        val product = ProductEntity(
            id = "test_product_4",
            name = "Pliers",
            description = "Long nose",
            price = 7.50,
            quantity = 30,
            imageUrl = null,
            createdBy = "user",
            version = 1,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED
        )
        productDao.insertProduct(product)

        // Act - Delete product offline
        val local = productDao.getProductById("test_product_4")
        assertNotNull(local)
        
        // Mark as PENDING_DELETE offline so it's hidden from list
        val deletedOffline = local!!.copy(syncStatus = SyncStatus.PENDING_DELETE)
        productDao.insertProduct(deletedOffline)
        syncDao.insertOperation(SyncOperationEntity(productId = "test_product_4", operationType = OperationType.DELETE))

        // Assert - Verified it is marked as PENDING_DELETE
        val markedProduct = productDao.getProductById("test_product_4")
        assertNotNull(markedProduct)
        assertEquals(SyncStatus.PENDING_DELETE, markedProduct!!.syncStatus)

        // Act - Connect to internet and simulate sync
        productDao.deleteProductById("test_product_4")
        syncDao.deleteOperationsForProduct("test_product_4")

        // Assert - Physically deleted from local DB
        val finalProduct = productDao.getProductById("test_product_4")
        assertEquals(null, finalProduct)
        assertEquals(0, syncDao.getPendingOperations().size)
    }
}

