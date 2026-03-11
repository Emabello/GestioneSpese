package com.emanuele.gestionespese.data.remote

import com.emanuele.gestionespese.data.model.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApi {

    @GET("exec")
    suspend fun getSpese(
        @Query("resource") resource: String = "spese",
        @Query("utente") utente: String
    ): ApiEnvelope<List<SpesaView>>

    // Login ora in POST: credenziali nel body, non nell'URL
    @POST("exec")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<Map<String, Any?>>

    // --- Lookups ---

    @GET("exec")
    suspend fun getTipi(
        @Query("resource") resource: String = "tipologia"
    ): ApiEnvelope<List<Map<String, Any?>>>

    @GET("exec")
    suspend fun getCategorie(
        @Query("resource") resource: String = "categoria"
    ): ApiEnvelope<List<CategoriaRow>>

    @GET("exec")
    suspend fun getSottocategorie(
        @Query("resource") resource: String = "sottocategoria"
    ): ApiEnvelope<List<Map<String, Any?>>>

    @GET("exec")
    suspend fun getConti(
        @Query("resource") resource: String = "conto",
        @Query("utenza") utente: String? = null
    ): ApiEnvelope<List<Map<String, Any?>>>

    @GET("exec")
    suspend fun getUtcs(
        @Query("resource") resource: String = "UTCS"
    ): ApiEnvelope<List<Map<String, Any?>>>

    // --- CRUD Spese ---

    @POST("exec")
    suspend fun insertSpesa(@Body req: InsertRequest<SpesaPatch>): ApiEnvelope<Map<String, Any?>>

    @POST("exec")
    suspend fun updateSpesa(@Body req: UpdateRequest<SpesaPatch>): ApiEnvelope<Map<String, Any?>>

    @POST("exec")
    suspend fun deleteSpesa(@Body req: DeleteRequest): ApiEnvelope<Map<String, Any?>>

    // --- Google Login ---
    @POST("exec")
    suspend fun linkGoogle(@Body body: LinkGoogleRequest): ApiEnvelope<Map<String, Any?>>

    @POST("exec")
    suspend fun unlinkGoogle(@Body body: UnlinkGoogleRequest): ApiEnvelope<Map<String, Any?>>

    @POST("exec")
    suspend fun loginGoogle(@Body body: GoogleLoginRequest): ApiEnvelope<Map<String, Any?>>
}