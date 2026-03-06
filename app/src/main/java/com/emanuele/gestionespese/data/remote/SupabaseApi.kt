package com.emanuele.gestionespese.data.remote

import com.emanuele.gestionespese.data.model.*
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query

interface SupabaseApi {

    @GET("exec")
    suspend fun getSpese(
        @Query("resource") resource: String = "spese",
        @Query("utente") utente: String
    ): ApiEnvelope<List<SpesaView>>

    @GET("exec")
    suspend fun getCategorie(
        @Query("resource") resource: String = "categoria"
    ): ApiEnvelope<List<CategoriaRow>>

    @GET("exec")
    suspend fun getSottoCategorie(
        @Query("resource") resource: String = "sottocategoria"
    ): ApiEnvelope<List<SottocategoriaRow>>

    // ✅ Lookups generici come Map per non dipendere dai campi
    @GET("exec")
    suspend fun getTipi(
        @Query("resource") resource: String = "tipologia"
    ): ApiEnvelope<List<Map<String, Any?>>>

    @GET("exec")
    suspend fun getSottocategorie(
        @Query("resource") resource: String = "sottocategoria"
    ): ApiEnvelope<List<Map<String, Any?>>>

    @GET("exec")
    suspend fun getConti(
        @Query("resource") resource: String = "conto",
        @Query("utente") utente: String? = null
    ): ApiEnvelope<List<Map<String, Any?>>>

    @GET("exec")
    suspend fun login(
        @Query("resource") resource: String = "utente",
        @Query("user") user: String,
        @Query("password") password: String
    ): ApiEnvelope<UtenteRow?>

    @GET("exec")
    suspend fun getUtcs(
        @Query("resource") resource: String = "UTCS"
    ): ApiEnvelope<List<Map<String, Any?>>>

    @POST("exec")
    suspend fun insertSpesa(@Body req: InsertRequest<SpesaPatch>): ApiEnvelope<Map<String, Any?>>

    @POST("exec")
    suspend fun updateSpesa(@Body req: UpdateRequest<SpesaPatch>): ApiEnvelope<Map<String, Any?>>

    @POST("exec")
    suspend fun deleteSpesa(@Body req: DeleteRequest): ApiEnvelope<Map<String, Any?>>
}