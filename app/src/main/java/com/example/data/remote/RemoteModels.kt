package com.example.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val username: String? = null,
    val password: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val user: RemoteUser? = null
)

@JsonClass(generateAdapter = true)
data class RemoteUser(
    val id: String,
    val username: String,
    val email: String
)

@JsonClass(generateAdapter = true)
data class RemoteProduct(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val quantity: Int,
    val imageUrl: String?,
    val createdBy: String?,
    val version: Int,
    val updatedAt: String // ISO 8601 String or Long String. Let's make sure we parse it safely.
)

@JsonClass(generateAdapter = true)
data class SyncUploadRequest(
    val created: List<RemoteProduct>,
    val updated: List<RemoteProduct>,
    val deleted: List<String>
)

@JsonClass(generateAdapter = true)
data class SyncUploadResponse(
    val success: Boolean,
    val conflicts: List<RemoteProduct>? = null, // Server version of products with version mismatch
    val updated: List<RemoteProduct>? = null    // Server state after successful sync
)

@JsonClass(generateAdapter = true)
data class SyncDownloadResponse(
    val products: List<RemoteProduct>,
    val lastSyncTime: String // Server returns the current server time for next sync
)
