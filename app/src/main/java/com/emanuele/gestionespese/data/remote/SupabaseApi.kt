package com.emanuele.gestionespese.data.remote

import com.emanuele.gestionespese.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApi {

    @GET("exec")
    suspend fun getSpeseView(
        @Query("resource") resource: String = "spese"
    ): List<SpesaView>

    @GET("exec")
    suspend fun getCategorie(
        @Query("resource") resource: String = "categorie"
    ): List<Categoria>

    @GET("exec")
    suspend fun getSottocategorieByCategoria(
        @Query("resource") resource: String = "sottocategorie",
        @Query("categoria_id") categoriaFilter: String
    ): List<LinkSottoRow>

    @GET("exec")
    suspend fun resolveCategoriaLinkId(
        @Query("resource") resource: String = "categoria_link",
        @Query("categoria_id") categoriaFilter: String,
        @Query("sottocategoria_id") sottocategoriaFilter: String?
    ): List<LinkIdRow>

    @POST("exec")
    suspend fun insertSpesa(
        @Body spesa: SpesaUpsertRequest
    ): Response<InsertSpesaResponse>

    @POST("exec")
    suspend fun updateSpesa(
        @Body req: UpdateSpesaRequest
    ): Response<Unit>

    @POST("exec")
    suspend fun deleteSpesa(
        @Body req: DeleteSpesaRequest
    ): Response<Unit>
}
