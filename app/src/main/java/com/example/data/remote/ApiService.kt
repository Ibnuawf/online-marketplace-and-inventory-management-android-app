package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    // Auth
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    // Products CRUD
    @GET("api/products")
    suspend fun getProducts(): Response<List<RemoteProduct>>

    @POST("api/products")
    suspend fun createProduct(@Body product: RemoteProduct): Response<RemoteProduct>

    @PUT("api/products/{id}")
    suspend fun updateProduct(
        @Path("id") id: String,
        @Body product: RemoteProduct
    ): Response<RemoteProduct>

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Path("id") id: String): Response<Unit>

    // Sync
    @POST("api/products/sync")
    suspend fun uploadSync(@Body request: SyncUploadRequest): Response<SyncUploadResponse>

    @GET("api/products/sync")
    suspend fun downloadSync(@Query("lastSyncTime") lastSyncTime: String): Response<SyncDownloadResponse>
}
