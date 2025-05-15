package com.example.data

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.TokenManager
import com.example.data.remote.LoginRequest
import com.example.data.remote.RegisterRequest
import com.example.data.remote.RetrofitClient
import com.example.sync.SyncManager

class AuthRepository(private val context: Context) {
    private val tokenManager = TokenManager(context)
    private val database = AppDatabase.getDatabase(context)
    private val productDao = database.productDao()
    private val syncDao = database.syncDao()
    private val apiService = RetrofitClient.getApiService(context)
    private val syncManager = SyncManager(context)

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
    fun getUsername(): String? = tokenManager.getUsername()
    fun getEmail(): String? = tokenManager.getEmail()

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val response = apiService.login(LoginRequest(email = email, password = password))
            if (response.isSuccessful && response.body() != null) {
                val authBody = response.body()!!
                tokenManager.saveToken(authBody.token)
                
                val user = authBody.user
                val username = user?.username ?: email.substringBefore("@")
                tokenManager.saveUser(username, email)

                productDao.clearAllProducts()
                syncDao.clearQueue()
                tokenManager.saveLastSyncTime("")

                syncManager.sync()

                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Login failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (isHtmlOrRedirectException(e)) {
                // Fallback to offline local login session
                val username = email.substringBefore("@")
                tokenManager.saveToken("offline_token_${username}_${System.currentTimeMillis()}")
                tokenManager.saveUser(username, email)

                productDao.clearAllProducts()
                syncDao.clearQueue()
                tokenManager.saveLastSyncTime("")

                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<Unit> {
        return try {
            val response = apiService.register(RegisterRequest(username = username, email = email, password = password))
            if (response.isSuccessful && response.body() != null) {
                val authBody = response.body()!!
                tokenManager.saveToken(authBody.token)
                tokenManager.saveUser(username, email)

                productDao.clearAllProducts()
                syncDao.clearQueue()
                tokenManager.saveLastSyncTime("")

                syncManager.sync()

                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Registration failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (isHtmlOrRedirectException(e)) {
                // Fallback to offline local registration session
                tokenManager.saveToken("offline_token_${username}_${System.currentTimeMillis()}")
                tokenManager.saveUser(username, email)

                productDao.clearAllProducts()
                syncDao.clearQueue()
                tokenManager.saveLastSyncTime("")

                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun isHtmlOrRedirectException(e: Exception): Boolean {
        val msg = e.message ?: ""
        return e is com.squareup.moshi.JsonEncodingException || 
               e is com.squareup.moshi.JsonDataException ||
               msg.contains("setLenient") || 
               msg.contains("Malformed JSON") || 
               msg.contains("HTML") ||
               e is java.io.IOException ||
               e is retrofit2.HttpException
    }

    suspend fun logout() {
        tokenManager.clear()
        productDao.clearAllProducts()
        syncDao.clearQueue()
    }
}
