package com.example.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.ProductEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TokenManager
import com.example.data.remote.RemoteProduct
import com.example.data.remote.RetrofitClient
import com.example.data.remote.SyncUploadRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncState {
    ONLINE,
    OFFLINE,
    SYNCING,
    CONFLICT_FOUND,
    IDLE
}

class SyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val productDao = database.productDao()
    private val syncDao = database.syncDao()
    private val tokenManager = TokenManager(context)
    private val networkMonitor = NetworkMonitor(context)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun sync(): Boolean {
        if (!networkMonitor.isOnline()) {
            _syncState.value = SyncState.OFFLINE
            return false
        }

        val token = tokenManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.d("SyncManager", "No auth token, skipping sync.")
            return false
        }

        try {
            _syncState.value = SyncState.SYNCING
            Log.d("SyncManager", "Starting synchronization...")

            val apiService = RetrofitClient.getApiService(context)

            val pendingCreates = productDao.getProductsWithStatus(SyncStatus.PENDING_CREATE)
            val pendingUpdates = productDao.getProductsWithStatus(SyncStatus.PENDING_UPDATE)
            val pendingDeletes = productDao.getProductsWithStatus(SyncStatus.PENDING_DELETE)

            val hasChanges = pendingCreates.isNotEmpty() || pendingUpdates.isNotEmpty() || pendingDeletes.isNotEmpty()

            if (hasChanges) {
                val createdRemotes = pendingCreates.map { ConflictResolver.toRemote(it) }
                val updatedRemotes = pendingUpdates.map { ConflictResolver.toRemote(it) }
                val deletedIds = pendingDeletes.map { it.id }

                val uploadRequest = SyncUploadRequest(
                    created = createdRemotes,
                    updated = updatedRemotes,
                    deleted = deletedIds
                )

                val uploadResponse = apiService.uploadSync(uploadRequest)
                if (uploadResponse.isSuccessful && uploadResponse.body() != null) {
                    val result = uploadResponse.body()!!
                    
                    pendingCreates.forEach {
                        productDao.insertProduct(it.copy(syncStatus = SyncStatus.SYNCED))
                    }
                    pendingUpdates.forEach {
                        productDao.insertProduct(it.copy(syncStatus = SyncStatus.SYNCED))
                    }
                    pendingDeletes.forEach {
                        productDao.deleteProductById(it.id)
                    }

                    pendingCreates.forEach { syncDao.deleteOperationsForProduct(it.id) }
                    pendingUpdates.forEach { syncDao.deleteOperationsForProduct(it.id) }
                    pendingDeletes.forEach { syncDao.deleteOperationsForProduct(it.id) }

                    if (!result.conflicts.isNullOrEmpty()) {
                        _syncState.value = SyncState.CONFLICT_FOUND
                        result.conflicts.forEach { conflict ->
                            val local = productDao.getProductById(conflict.id)
                            if (local != null) {
                                val resolved = ConflictResolver.resolveServerWins(local, conflict)
                                productDao.insertProduct(resolved)
                                syncDao.deleteOperationsForProduct(conflict.id)
                            }
                        }
                    }
                } else {
                    Log.e("SyncManager", "Upload sync failed: ${uploadResponse.errorBody()?.string()}")
                    _syncState.value = SyncState.ONLINE
                    return false
                }
            }

            val lastSyncTime = tokenManager.getLastSyncTime()
            val downloadResponse = apiService.downloadSync(lastSyncTime)
            
            if (downloadResponse.isSuccessful && downloadResponse.body() != null) {
                val syncData = downloadResponse.body()!!
                val serverProducts = syncData.products

                serverProducts.forEach { remote ->
                    val local = productDao.getProductById(remote.id)
                    if (local == null) {
                        productDao.insertProduct(ConflictResolver.fromRemote(remote))
                    } else {
                        if (local.syncStatus == SyncStatus.SYNCED) {
                            productDao.insertProduct(ConflictResolver.fromRemote(remote))
                        } else {
                            _syncState.value = SyncState.CONFLICT_FOUND
                            val resolved = ConflictResolver.resolveServerWins(local, remote)
                            productDao.insertProduct(resolved)
                            syncDao.deleteOperationsForProduct(remote.id)
                        }
                    }
                }

                tokenManager.saveLastSyncTime(syncData.lastSyncTime)
            } else {
                Log.e("SyncManager", "Download sync failed: ${downloadResponse.errorBody()?.string()}")
                _syncState.value = SyncState.ONLINE
                return false
            }

            _syncState.value = SyncState.ONLINE
            Log.d("SyncManager", "Synchronization complete!")
            return true
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync error: ", e)
            _syncState.value = if (networkMonitor.isOnline()) SyncState.ONLINE else SyncState.OFFLINE
            return false
        }
    }
}
