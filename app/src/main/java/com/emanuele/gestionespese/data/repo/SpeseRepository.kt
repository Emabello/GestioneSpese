package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.model.DeleteSpesaRequest
import com.emanuele.gestionespese.data.model.SpesaUpsert
import com.emanuele.gestionespese.data.model.SpesaUpsertRequest
import com.emanuele.gestionespese.data.model.UpdateSpesaRequest
import com.emanuele.gestionespese.data.remote.SupabaseApi
import retrofit2.HttpException

class SpeseRepository(private val api: SupabaseApi) {

    suspend fun list() = api.getSpeseView()

    suspend fun listCategorie() = api.getCategorie()

    suspend fun listSottocategorie(categoriaId: String) =
        api.getSottocategorieByCategoria(categoriaFilter = categoriaId)
            .mapNotNull { it.sottocategoria }

    suspend fun add(spesa: SpesaUpsert) {
        val (categoria, sottocategoria) = splitLinkId(spesa.categoriaLinkId)
        val response = api.insertSpesa(
            SpesaUpsertRequest(
                contoId = spesa.metodoPagamento,
                data = spesa.data,
                importo = spesa.importo,
                tipo = spesa.tipo,
                categoria = categoria,
                sottocategoria = sottocategoria,
                descrizione = spesa.note
            )
        )
        if (!response.isSuccessful) {
            val body = response.errorBody()?.string()
            throw IllegalStateException("INSERT failed ${response.code()} - $body")
        }
    }

    suspend fun update(id: Int, spesa: SpesaUpsert) {
        val (categoria, sottocategoria) = splitLinkId(spesa.categoriaLinkId)
        val response = api.updateSpesa(
            UpdateSpesaRequest(
                id = id,
                contoId = spesa.metodoPagamento,
                data = spesa.data,
                importo = spesa.importo,
                tipo = spesa.tipo,
                categoria = categoria,
                sottocategoria = sottocategoria,
                descrizione = spesa.note
            )
        )
        if (!response.isSuccessful) throw HttpException(response)
    }

    suspend fun delete(id: Int) {
        val response = api.deleteSpesa(DeleteSpesaRequest(id = id))
        if (!response.isSuccessful) throw HttpException(response)
    }

    suspend fun resolveCategoriaLinkId(categoriaId: String, sottocategoriaId: String?): String {
        val rows = api.resolveCategoriaLinkId(
            categoriaFilter = categoriaId,
            sottocategoriaFilter = sottocategoriaId
        )
        return rows.firstOrNull()?.id
            ?: throw IllegalStateException("Nessun categoria_link_id trovato per categoria=$categoriaId sottocategoria=$sottocategoriaId")
    }

    private fun splitLinkId(linkId: String): Pair<String, String> {
        val parts = linkId.split("|", limit = 2)
        val categoria = parts.getOrNull(0).orEmpty()
        val sottocategoria = parts.getOrNull(1).orEmpty()
        return categoria to sottocategoria
    }
}
