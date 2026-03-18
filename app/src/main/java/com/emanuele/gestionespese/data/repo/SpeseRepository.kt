/**
 * SpeseRepository.kt
 *
 * Repository principale dell'app. Gestisce tutta la logica di accesso ai dati
 * per le spese e le tabelle di lookup, coordinando:
 * - Il database locale Room ([SpesaDao], [LookupDao])
 * - Il backend remoto Google Apps Script ([SupabaseApi])
 *
 * Strategia dati: **offline-first con sync esplicito**.
 * Le letture avvengono sempre da Room; la sincronizzazione con il backend
 * viene avviata esplicitamente da [SpeseViewModel.syncAll].
 *
 * In fondo al file si trovano i mapper privati tra [SpesaView] ↔ [SpesaEntity].
 */
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

/**
 * Repository per spese e lookup. Dipende da [SupabaseApi], [LookupDao] e [SpesaDao].
 *
 * @param api        Client Retrofit per le chiamate al backend.
 * @param lookupDao  DAO per le tabelle di lookup (tipi, categorie, ecc.).
 * @param spesaDao   DAO per la tabella `spese`.
 */
class SpeseRepository(
    private val api: SupabaseApi,
    private val lookupDao: LookupDao,
    private val spesaDao: SpesaDao
) {

    // ── Lettura da Room ───────────────────────────────────────────────────────

    /**
     * Restituisce la lista completa di movimenti dell'utente dal DB locale.
     *
     * @param utente ID dell'utente.
     * @return Lista di [SpesaView] ordinata per data decrescente.
     */
    suspend fun list(utente: String): List<SpesaView> =
        spesaDao.getByUtente(utente).map { it.toSpesaView() }

    // ── Sync remoto → Room ────────────────────────────────────────────────────

    /**
     * Scarica le spese dal backend e sostituisce il contenuto di Room.
     * Operazione distruttiva: cancella tutte le spese locali dell'utente
     * prima di inserire quelle remote.
     *
     * @param utente ID dell'utente.
     */
    suspend fun syncSpese(utente: String) {
        val remoteList = api.getSpese(utente = utente).data ?: emptyList()
        val entities = remoteList.map { it.toEntity(utente) }
        spesaDao.clearByUtente(utente)
        spesaDao.upsertAll(entities)
    }

    // ── Sync completo ─────────────────────────────────────────────────────────

    /**
     * Esegue la sincronizzazione completa di spese e lookup in parallelo.
     * Usato da [SpeseViewModel.syncAll] all'avvio e su richiesta esplicita.
     *
     * @param utente ID dell'utente.
     */
    suspend fun syncAll(utente: String) {
        coroutineScope {
            val dSpese   = async { syncSpese(utente) }
            val dLookups = async { refreshLookupsFromRemoteAndSave(utenteId = utente) }
            dSpese.await()
            dLookups.await()
        }
    }

    // ── Scrittura (remote + aggiorna Room) ───────────────────────────────────

    /**
     * Inserisce una nuova spesa sul backend e aggiorna la cache locale.
     *
     * @throws IllegalStateException se il backend restituisce un errore.
     */
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

    /**
     * Aggiorna una spesa esistente sul backend e sincronizza Room.
     *
     * @param id ID del movimento da aggiornare.
     * @throws IllegalStateException se il backend restituisce un errore.
     */
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

    /**
     * Elimina una spesa dal backend e dal DB locale.
     *
     * @param id     ID del movimento da eliminare.
     * @param utente ID dell'utente (non usato direttamente ma mantenuto per coerenza API).
     * @throws IllegalStateException se il backend restituisce un errore.
     */
    suspend fun delete(id: Int, utente: String) {
        val res = api.deleteSpesa(DeleteRequest(resource = "spese", id = id))
        if (res.error != null) throw IllegalStateException(res.error)
        spesaDao.deleteById(id)
    }

    // ── Lookup da remoto ──────────────────────────────────────────────────────

    /** Scarica e filtra i tipi di movimento dal backend. */
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

    /** Scarica i tipi raw (mappa JSON) per estrarre `tipo_movimento`. */
    private suspend fun getTipiRaw(): List<Map<String, Any?>> =
        api.getTipi().data ?: emptyList()

    /** Scarica e filtra le categorie attive dal backend. */
    private suspend fun getCategorie(): List<String> {
        val rows: List<CategoriaRow> = api.getCategorie().data ?: emptyList()
        return rows
            .filter { it.attiva.asBoolDefaultTrue() }
            .mapNotNull { buildLabel(it.id.toString(), it.descrizione) }
            .distinct().sorted()
    }

    /** Scarica e filtra le sottocategorie attive dal backend. */
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

    /**
     * Scarica i conti attivi dell'utente dal backend.
     *
     * @param utenteId ID dell'utente (usato per filtrare i conti sul backend).
     */
    private suspend fun getConti(utenteId: String? = null): List<String> {
        val rows: List<Map<String, Any?>> = api.getUc(utente = utenteId).data ?: emptyList()
        return rows
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull { m -> m.firstNonBlank("id_conto", "ID_CONTO")?.trim() }
            .distinct()
            .sorted()
    }

    // ── Cache locale (Room) ───────────────────────────────────────────────────

    /**
     * Carica tutte le lookup tables dal DB locale in un'unica struttura [LookupsLocal].
     *
     * @param utenteId ID utente per filtrare i conti (opzionale).
     * @return Aggregazione di tipi, categorie, sottocategorie e conti.
     */
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

    /**
     * Carica le associazioni UTC dal DB locale.
     *
     * @param utenteId Se specificato, filtra solo gli UTC dell'utente indicato.
     * @return Lista di [UtcItem] per la compilazione automatica del form spese.
     */
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

    /**
     * Scarica tutte le lookup dal backend in parallelo e aggiorna il DB locale.
     * Le 6 chiamate API (tipi, tipiRaw, categorie, sottocategorie, conti, UTC)
     * vengono lanciate in parallelo con [async] per minimizzare la latenza.
     *
     * @param utenteId ID dell'utente per filtrare i conti.
     */
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

// ── Mapper privati ────────────────────────────────────────────────────────────

/**
 * Converte un [SpesaView] ricevuto dall'API in una [SpesaEntity] per Room.
 * Estrae mese e anno dalla stringa data nel formato `"YYYY-MM-DD"`.
 */
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

/** Converte una [SpesaEntity] da Room in un [SpesaView] usato dalla UI. */
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