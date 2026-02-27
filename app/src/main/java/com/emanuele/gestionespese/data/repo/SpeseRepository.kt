package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.model.*
import com.emanuele.gestionespese.data.remote.SupabaseApi
import retrofit2.HttpException


class SpeseRepository(private val api: SupabaseApi) {

    // VIEW
    suspend fun list(): List<SpesaView> =
        api.getSpeseView()

    // LOOKUP
    suspend fun listCategorie(): List<Categoria> =
        api.getCategorie()

    suspend fun listSottocategorie(categoriaId: String): List<Sottocategoria> =
        api.getSottocategorieByCategoria(categoriaFilter = "eq.$categoriaId")
            .mapNotNull { it.sottocategoria }

    // INSERT
    suspend fun add(spesa: SpesaUpsert) {
        val response = api.insertSpesa(spesa)
        if (!response.isSuccessful) {
            val body = response.errorBody()?.string()
            throw IllegalStateException("INSERT failed ${response.code()} - $body")
        }
    }

    // UPDATE
    suspend fun update(id: Int, spesa: SpesaUpsert) {
        val response = api.updateSpesa("eq.$id", spesa)
        if (!response.isSuccessful) throw HttpException(response)
    }

    // DELETE
    suspend fun delete(id: Int) {
        val response = api.deleteSpesa("eq.$id")
        if (!response.isSuccessful) throw HttpException(response)
    }

    // RESOLVE
    suspend fun resolveCategoriaLinkId(categoriaId: String, sottocategoriaId: String?): String {
        val subFilter = if (sottocategoriaId == null) "is.null" else "eq.$sottocategoriaId"
        val rows = api.resolveCategoriaLinkId(
            categoriaFilter = "eq.$categoriaId",
            sottocategoriaFilter = subFilter
        )
        return rows.firstOrNull()?.id
            ?: throw IllegalStateException("Nessun categoria_link_id trovato per categoria=$categoriaId sottocategoria=$sottocategoriaId")
    }

    suspend fun addViaRpc(req: RpcInsertSpesaRequest) {
        val resp = api.insertSpesaFirstFreeId(req)
        if (!resp.isSuccessful) {
            val body = resp.errorBody()?.string()
            throw IllegalStateException("RPC INSERT failed ${resp.code()} - $body")
        }
    }
}
