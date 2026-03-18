/**
 * SupabaseApi.kt
 *
 * Interfaccia Retrofit per tutte le chiamate al backend Google Apps Script.
 * Nonostante il nome (legacy da Supabase), il backend è ora un Apps Script
 * che espone un endpoint GET/POST su `exec`.
 *
 * Tutte le chiamate sono `suspend` e ritornano [ApiEnvelope] con il tipo
 * di risposta appropriato. Gli errori di rete lanciano eccezioni gestite
 * dal repository chiamante.
 */
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
    suspend fun getUc(
        @Query("resource") resource: String = "uc",
        @Query("utente") utente: String? = null
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

    @GET("exec")
    suspend fun getDashboard(
        @Query("resource") resource: String = "dashboard",
        @Query("utente") utente: String
    ): ApiEnvelope<List<Map<String, Any?>>>

    @POST("exec")
    suspend fun saveDashboard(@Body body: SaveDashboardRequest): ApiEnvelope<Map<String, Any?>>

    // ── Config CRUD generico ─────────────────────────────────────────────

    @GET("exec")
    suspend fun getConti(
        @Query("resource") resource: String = "conto"
    ): ApiEnvelope<List<Map<String, Any?>>>

    // Insert generico
    @POST("exec")
    suspend fun insertRecord(@Body req: GenericInsertRequest): ApiEnvelope<Map<String, Any?>>

    // Update generico
    @POST("exec")
    suspend fun updateRecord(@Body req: GenericUpdateRequest): ApiEnvelope<Map<String, Any?>>

    // Delete generico
    @POST("exec")
    suspend fun deleteRecord(@Body req: GenericDeleteRequest): ApiEnvelope<Map<String, Any?>>
}