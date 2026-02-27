package com.emanuele.gestionespese.data.remote

import com.emanuele.gestionespese.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApi {

    // =========================
    // SPESE (VIEW)
    // =========================

    @GET("rest/v1/v_spese")
    suspend fun getSpeseView(
        @Query("select") select: String = "*",
        @Query("order") order: String = "data.desc"
    ): List<SpesaView>


    // =========================
    // CATEGORIE
    // =========================

    @GET("rest/v1/cfg_categorie")
    suspend fun getCategorie(
        @Query("select") select: String = "*",
        @Query("order") order: String = "ordine.asc"
    ): List<Categoria>


    // =========================
    // SOTTOCATEGORIE PER CATEGORIA
    // (via tabella ponte)
    // =========================

    @GET("rest/v1/cfg_categoria_sottocategoria")
    suspend fun getSottocategorieByCategoria(
        @Query("select")
        select: String =
            "ordine,sottocategoria:cfg_sottocategorie(id,nome,ordine,attiva)",

        @Query("categoria_id")
        categoriaFilter: String,

        @Query("attiva")
        attiva: String = "eq.true",

        @Query("order")
        order: String = "ordine.asc"
    ): List<LinkSottoRow>


    // =========================
    // RESOLVE categoria_link_id
    // =========================

    @GET("rest/v1/cfg_categoria_sottocategoria")
    suspend fun resolveCategoriaLinkId(
        @Query("select")
        select: String = "id",

        @Query("categoria_id")
        categoriaFilter: String,

        @Query("sottocategoria_id")
        sottocategoriaFilter: String,

        @Query("limit")
        limit: Int = 1
    ): List<LinkIdRow>


    // =========================
    // INSERT
    // =========================

    @POST("rest/v1/spese")
    suspend fun insertSpesa(
        @Body spesa: SpesaUpsert
    ): Response<Unit>


    // =========================
    // UPDATE (PATCH)
    // =========================
    // IMPORTANTE: usare @Query e non @Path

    @PATCH("rest/v1/spese")
    suspend fun updateSpesa(
        @Query("id") idFilter: String,
        @Body spesa: SpesaUpsert
    ): Response<Unit>


    // =========================
    // DELETE
    // =========================

    @DELETE("rest/v1/spese")
    suspend fun deleteSpesa(
        @Query("id") idFilter: String
    ): Response<Unit>

    @POST("rest/v1/rpc/insert_spesa_first_free_id")
    suspend fun insertSpesaFirstFreeId(
        @Body req: RpcInsertSpesaRequest
    ): retrofit2.Response<SpesaRow>
}