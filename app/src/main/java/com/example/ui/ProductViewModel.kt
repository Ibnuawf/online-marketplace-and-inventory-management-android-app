package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ProductRepository
import com.example.data.local.ProductEntity
import com.example.data.local.SyncStatus
import com.example.sync.NetworkMonitor
import com.example.sync.SyncManager
import com.example.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppNetworkStatus {
    ONLINE,
    OFFLINE,
    SYNCING,
    CONFLICT_FOUND
}

class ProductViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProductRepository(application)
    private val syncManager = SyncManager(application)
    private val networkMonitor = NetworkMonitor(application)

    val productsState: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val appStatusState: StateFlow<AppNetworkStatus> = combine(
        networkMonitor.isOnlineFlow,
        syncManager.syncState
    ) { isOnline, syncState ->
        when {
            !isOnline -> AppNetworkStatus.OFFLINE
            syncState == SyncState.SYNCING -> AppNetworkStatus.SYNCING
            syncState == SyncState.CONFLICT_FOUND -> AppNetworkStatus.CONFLICT_FOUND
            else -> AppNetworkStatus.ONLINE
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppNetworkStatus.ONLINE
    )

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun clearMessage() {
        _uiMessage.value = null
    }

    fun createProduct(name: String, description: String, price: Double, quantity: Int) {
        viewModelScope.launch {
            try {
                repository.createProduct(name, description, price, quantity)
                _uiMessage.value = "Product created locally!"
            } catch (e: Exception) {
                _uiMessage.value = "Error creating product: ${e.localizedMessage}"
            }
        }
    }

    fun updateProduct(id: String, name: String, description: String, price: Double, quantity: Int, currentVersion: Int) {
        viewModelScope.launch {
            try {
                repository.updateProduct(id, name, description, price, quantity, currentVersion = currentVersion)
                _uiMessage.value = "Product updated locally!"
            } catch (e: Exception) {
                _uiMessage.value = "Error updating product: ${e.localizedMessage}"
            }
        }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(id)
                _uiMessage.value = "Product deleted!"
            } catch (e: Exception) {
                _uiMessage.value = "Error deleting product: ${e.localizedMessage}"
            }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            _uiMessage.value = "Syncing..."
            val success = syncManager.sync()
            if (success) {
                _uiMessage.value = "Sync completed successfully!"
            } else {
                _uiMessage.value = "Sync completed. Check connection or logs."
            }
        }
    }

    // Factory helper
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProductViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ProductViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
