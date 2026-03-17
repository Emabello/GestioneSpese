package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.LookupDao
import com.emanuele.gestionespese.data.local.SpesaDao
import com.emanuele.gestionespese.data.local.entities.*
import com.emanuele.gestionespese.data.local.sottoKey
import com.emanuele.gestionespese.data.local.utcKey
import com.emanuele.gestionespese.data.model.*
import com.emanuele.gestionespese.data.remote.SupabaseApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SpeseRepository(
    private val api: SupabaseApi,
    private val lookupDao: LookupDao,
    private val spesaDao: SpesaDao
) {

    /** ===================== SPESE — lettura da Room ===================== **/

    suspend fun list(utente: String): List<SpesaView> =
        spesaDao.getByUtente(utente).map { it.toSpesaView() }

    /** ===================== SPESE — sync remoto → Room ===================== **/

    suspend fun syncSpese(utente: String) {
        val remoteList = api.getSpese(utente = utente).data ?: emptyList()
        val entities = remoteList.map { it.toEntity(utente) }
        spesaDao.clearByUtente(utente)
        spesaDao.upsertAll(entities)
    }

    /** ===================== SYNC COMPLETO — spese + lookups in parallelo ===================== **/

    suspend fun syncAll(utente: String) {
        coroutineScope {
            val dSpese   = async { syncSpese(utente) }
            val dLookups = async { refreshLookupsFromRemoteAndSave(utenteId = utente) }
            dSpese.await()
            dLookups.await()
        }
    }

    /** ===================== SPESE — scrittura (remote + aggiorna Room) ===================== **/

    suspend fun add(
        utente: String,
        data: String,
        conto: String,
        importo: Double,
        tipo: String,
        categoria: String?,
        sottocategoria: String?,
        descrizione: String?
    ) {
        val res = api.insertSpesa(
            InsertRequest(
                resource = "spese",
                utente = utente,
                data = SpesaPatch(
                    utente = utente,
                    data = data,
                    conto = conto,
                    importo = importo,
                    tipo = tipo,
                    categoria = categoria,
                    sottocategoria = sottocategoria,
                    descrizione = descrizione
                )
            )
        )
        if (res.error != null) throw IllegalStateException(res.error)
        syncSpese(utente)
    }

    suspend fun update(
        id: Int,
        utente: String,
        data: String,
        conto: String,
        importo: Double,
        tipo: String,
        categoria: String?,
        sottocategoria: String?,
        descrizione: String?
    ) {
        val res = api.updateSpesa(
            UpdateRequest(
                resource = "spese",
                id = id,
                data = SpesaPatch(
                    utente = utente,
                    data = data,
                    conto = conto,
                    importo = importo,
                    tipo = tipo,
                    categoria = categoria,
                    sottocategoria = sottocategoria,
                    descrizione = descrizione
                )
            )
        )
        if (res.error != null) throw IllegalStateException(res.error)
        syncSpese(utente)
    }

    suspend fun delete(id: Int, utente: String) {
        val res = api.deleteSpesa(DeleteRequest(resource = "spese", id = id))
        if (res.error != null) throw IllegalStateException(res.error)
        spesaDao.deleteById(id)
    }

    /** ===================== LOOKUPS (REMOTE) ===================== **/

    private suspend fun getTipi(): List<String> {
        val rows: List<Map<String, Any?>> = api.getTipi().data ?: emptyList()
        return rows
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull {
                buildLabel(
                    it.firstNonBlank("id")?.numToCleanString(),
                    it.firstNonBlank("descrizione", "nome", "label")
                )
            }
            .distinct().sorted()
    }

    private suspend fun getTipiRaw(): List<Map<String, Any?>> =
        api.getTipi().data ?: emptyList()

    private suspend fun getCategorie(): List<String> {
        val rows: List<CategoriaRow> = api.getCategorie().data ?: emptyList()
        return rows
            .filter { it.attiva.asBoolDefaultTrue() }
            .mapNotNull { buildLabel(it.id.toString(), it.descrizione) }
            .distinct().sorted()
    }

    private suspend fun getSottocategorie(): List<SottoCatItem> {
        val rows: List<Map<String, Any?>> = api.getSottocategorie().data ?: emptyList()
        return rows
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull { m ->
                val catId = m.firstNonBlank("id_categoria", "categoria", "idCategoria")?.numToCleanString()
                val sub   = m.firstNonBlank("descrizione", "sottocategoria", "nome", "label")
                if (catId.isNullOrBlank() || sub.isNullOrBlank()) null
                else SottoCatItem(categoria = catId.trim(), sottocategoria = sub.trim())
            }
            .distinctBy { it.categoria.lowercase() + "||" + it.sottocategoria.lowercase() }
            .sortedWith(compareBy({ it.categoria.toIntOrNull() ?: Int.MAX_VALUE }, { it.sottocategoria.lowercase() }))
    }

    private suspend fun getConti(utenteId: String? = null): List<String> {
        val rows: List<Map<String, Any?>> = api.getUc(utente = utenteId).data ?: emptyList()
        return rows
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull { m -> m.firstNonBlank("id_conto", "ID_CONTO")?.trim() }
            .distinct()
            .sorted()
    }

    /** ===================== CACHE (ROOM) ===================== **/

    suspend fun getLookupsFromDb(utenteId: String? = null): LookupsLocal {
        val conti = if (!utenteId.isNullOrBlank())
            lookupDao.getConti(utenteId)
        else
            lookupDao.getConti("")
        return LookupsLocal(
            tipi           = lookupDao.getTipi(),
            categorie      = lookupDao.getCategorie(),
            sottocategorie = lookupDao.getSottocategoriePairs()
                .map { SottoCatItem(it.categoria, it.sottocategoria) },
            conti          = conti
        )
    }

    suspend fun getUtcsFromDb(utenteId: String? = null): List<UtcItem> {
        val entities = if (!utenteId.isNullOrBlank())
            lookupDao.getUtcsByUtente(utenteId)
        else
            lookupDao.getUtcs()
        return entities.map {
            UtcItem(
                utente         = it.utente,
                tipologia      = it.tipologia,
                categoria      = it.categoria,
                sottocategoria = it.sottocategoria,
                attivo         = it.attivo,
                tipoMovimento  = it.tipoMovimento
            )
        }
    }

    suspend fun refreshLookupsFromRemoteAndSave(utenteId: String? = null) {

        // ── Lancia tutte le 6 chiamate API in parallelo ───────────────────────
        val tipiList:      List<String>
        val categorieList: List<String>
        val sottoList:     List<SottoCatItem>
        val contiList:     List<String>
        val utcRowsList:   List<Map<String, Any?>>
        val tipiRawList:   List<Map<String, Any?>>

        coroutineScope {
            val dTipi      = async { getTipi() }
            val dCategorie = async { getCategorie() }
            val dSotto     = async { getSottocategorie() }
            val dConti     = async { getConti(utenteId) }
            val dUtcRows   = async { api.getUtcs().data ?: emptyList<Map<String, Any?>>() }
            val dTipiRaw   = async { getTipiRaw() }

            tipiList      = dTipi.await()
            categorieList = dCategorie.await()
            sottoList     = dSotto.await()
            contiList     = dConti.await()
            utcRowsList   = dUtcRows.await()
            tipiRawList   = dTipiRaw.await()
        }


        // ── Scrittura su Room ─────────────────────────────────────────────────
        lookupDao.clearTipi()
        lookupDao.upsertTipi(tipiList.map { TipoEntity(it) })
        lookupDao.clearCategorie()
        lookupDao.upsertCategorie(categorieList.map { CategoriaEntity(it) })
        lookupDao.clearConti()
        lookupDao.upsertConti(contiList.map { ContoEntity(value = it, utenteId = utenteId ?: "") })
        lookupDao.clearSottocategorie()
        lookupDao.upsertSottocategorie(sottoList.map {
            SottoCategoriaEntity(
                key            = sottoKey(it.categoria, it.sottocategoria),
                categoria      = it.categoria.trim(),
                sottocategoria = it.sottocategoria.trim()
            )
        })

        // ── Costruzione mappa tipologia → tipoMovimento ───────────────────────
        val tipologiaTipoMap: Map<String, String> = tipiRawList.associate { m ->
            val label = buildLabel(
                m.firstNonBlank("id")?.numToCleanString(),
                m.firstNonBlank("descrizione", "nome", "label")
            ) ?: ""
            label to (m.firstNonBlank("tipo_movimento", "TIPO_MOVIMENTO") ?: "uscita")
        }

        // ── UTC entities ──────────────────────────────────────────────────────
        val utcEntities = utcRowsList.mapNotNull { m ->
            val utente         = m.firstNonBlank("id_utente", "utenza", "ID_UTENTE")?.trim()
            val tipologia      = m.firstNonBlank("id_tipologia", "tipologia", "ID_TIPOLOGIA")?.trim()
            val categoria      = m.firstNonBlank("id_categoria", "categoria", "ID_CATEGORIA")?.trim()
            val sottocategoria = m.firstNonBlank("id_sottocategoria", "sottocategoria", "ID_SOTTOCATEGORIA")?.trim()
            if (utente.isNullOrBlank() || tipologia.isNullOrBlank() ||
                categoria.isNullOrBlank() || sottocategoria.isNullOrBlank()) return@mapNotNull null

            UtcEntity(
                key            = utcKey(utente, tipologia, categoria, sottocategoria),
                utente         = utente,
                tipologia      = tipologia,
                categoria      = categoria,
                sottocategoria = sottocategoria,
                attivo         = (m["attivo"] ?: m["ATTIVO"]).asBoolDefaultTrue(),
                tipoMovimento  = tipologiaTipoMap[tipologia] ?: "uscita"
            )
        }

        lookupDao.clearUtcs()
        lookupDao.upsertUtcs(utcEntities)
    }
}

/** ===================== MAPPERS ===================== **/

private fun SpesaView.toEntity(utente: String): SpesaEntity {
    val parts = data?.split("-")
    return SpesaEntity(
        id             = id,
        utente         = utente,
        data           = data ?: "",
        importo        = importo,
        tipo           = tipo ?: "",
        tipoMovimento  = tipo_movimento,
        conto          = conto,
        categoria      = categoria,
        sottocategoria = sottocategoria,
        descrizione    = descrizione,
        mese           = parts?.getOrNull(1)?.toIntOrNull(),
        anno           = parts?.getOrNull(0)?.toIntOrNull()
    )
}

private fun SpesaEntity.toSpesaView() = SpesaView(
    id             = id,
    utente         = utente,
    data           = data,
    importo        = importo,
    tipo           = tipo,
    tipo_movimento = tipoMovimento,
    conto          = conto,
    categoria      = categoria,
    sottocategoria = sottocategoria,
    descrizione    = descrizione
)