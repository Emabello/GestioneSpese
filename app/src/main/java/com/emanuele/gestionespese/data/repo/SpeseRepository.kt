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
 * In fondo al file si trovano:
 * - Mapper privati tra [SpesaView] ↔ [SpesaEntity]
 * - Parser privati condivisi tra [syncAllBatch] e [refreshLookupsFromRemoteAndSave]
 */
package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.LookupDao
import com.emanuele.gestionespese.data.local.SpesaDao
import com.emanuele.gestionespese.data.local.entities.*
import com.emanuele.gestionespese.data.local.sottoKey
import com.emanuele.gestionespese.data.local.utcKey
import com.emanuele.gestionespese.data.model.*
import com.emanuele.gestionespese.data.remote.SupabaseApi
import java.io.IOException

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
        refreshLookupsFromRemoteAndSave(utenteId = utente)
        syncSpese(utente)
    }

    /**
     * Sincronizzazione batch: una sola chiamata GAS restituisce tutti i dati
     * (tipologie, categorie, sottocategorie, conti, utcs, spese) in un'unica risposta.
     *
     * Se il backend non supporta ancora `sync_all` (risposta null o vuota),
     * fa automaticamente fallback alle 6 chiamate sequenziali di [syncAll].
     *
     * @param utente ID dell'utente.
     */
    suspend fun syncAllBatch(utente: String) {
        val data = runCatching { api.getSyncAll(utente = utente).data }.getOrNull()

        if (data == null) {
            // Fallback alle 6 chiamate sequenziali
            syncAll(utente)
            return
        }
        // ── Parse tipologie ───────────────────────────────────────────────
        val tipiRawList = data.tipologie ?: emptyList()
        val tipiList    = tipiRawList.toTipiEntitiesFull()

        // ── Parse categorie ───────────────────────────────────────────────
        val categorieList = (data.categorie ?: emptyList()).toCategorieEntities()

        // ── Parse sottocategorie ──────────────────────────────────────────
        val sottoList = (data.sottocategorie ?: emptyList()).toSottocategorieEntities()

        // ── Parse conti ───────────────────────────────────────────────────
        val contiList = (data.conti ?: emptyList()).toContiEntities(utente)

        // ── Parse UTCs ────────────────────────────────────────────────────
        val tipologiaTipoMap = buildTipologiaTipoMap(tipiRawList)
        val utcEntities      = (data.utcs ?: emptyList()).toUtcEntities(tipologiaTipoMap)

        if (tipiList.isEmpty() && categorieList.isEmpty() && contiList.isEmpty()) {
            // Risposta batch vuota → fallback
            syncAll(utente)
            return
        }

        // ── Scrittura su Room ─────────────────────────────────────────────
        lookupDao.clearTipi()
        lookupDao.upsertTipi(tipiList)
        lookupDao.clearCategorie()
        lookupDao.upsertCategorie(categorieList)
        lookupDao.clearConti()
        lookupDao.upsertConti(contiList)
        lookupDao.clearSottocategorie()
        lookupDao.upsertSottocategorie(sottoList)
        lookupDao.clearUtcs()
        lookupDao.upsertUtcs(utcEntities)

        // ── Spese ─────────────────────────────────────────────────────────
        val speseEntities = (data.spese ?: emptyList()).map { it.toEntity(utente) }
        spesaDao.clearByUtente(utente)
        spesaDao.upsertAll(speseEntities)
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
        contoDestinazione: String? = null,
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
                    conto_destinazione = contoDestinazione,
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
        contoDestinazione: String? = null,
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
                    conto_destinazione = contoDestinazione,
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

    /** Scarica e filtra le categorie attive dal backend. */
    private suspend fun getCategorie(): List<String> {
        val rows: List<CategoriaRow> = api.getCategorie().data ?: emptyList()
        return rows
            .filter { it.attiva.asBoolDefaultTrue() }
            .mapNotNull { buildLabel(it.id.toString(), it.descrizione) }
            .distinct().sorted()
    }

    /** Scarica e filtra le sottocategorie attive dal backend. */
    private suspend fun getSottocategorie(): List<SottoCategoriaEntity> {
        val rows: List<Map<String, Any?>> = api.getSottocategorie().data ?: emptyList()
        return rows
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull { m ->
                val catId = m.firstNonBlank("id_categoria", "categoria", "idCategoria")?.numToCleanString()
                val sub   = m.firstNonBlank("descrizione", "sottocategoria", "nome", "label")
                if (catId.isNullOrBlank() || sub.isNullOrBlank()) null
                else SottoCategoriaEntity(
                    key            = sottoKey(catId.trim(), sub.trim()),
                    id             = m.firstNonBlank("id")?.numToCleanString()?.toIntOrNull() ?: 0,
                    categoria      = catId.trim(),
                    sottocategoria = sub.trim(),
                    attivo         = m.isActiveDefaultTrue()
                )
            }
            .distinctBy { it.key }
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
     * Scarica tutte le lookup dal backend in modo sequenziale e aggiorna il DB locale.
     * Le 5 chiamate API (tipi, categorie, sottocategorie, conti, UTC) vengono eseguite
     * in sequenza per rispettare i rate limit di Google Apps Script.
     * Se tutte falliscono viene lanciata [IOException].
     *
     * Usato come fallback di [syncAllBatch] quando il backend non supporta `sync_all`.
     *
     * @param utenteId ID dell'utente per filtrare i conti.
     */
    suspend fun refreshLookupsFromRemoteAndSave(utenteId: String? = null) {

        // ── Chiamate SEQUENZIALI — GAS non regge le parallele ─────────────
        val tipiRawList   = runCatching { api.getTipi().data     ?: emptyList<Map<String, Any?>>() }.getOrDefault(emptyList())
        val categorieList = runCatching { getCategorie()         }.getOrDefault(emptyList())
        val sottoList     = runCatching { getSottocategorie()    }.getOrDefault(emptyList())
        val contiList     = runCatching { getConti(utenteId)     }.getOrDefault(emptyList())
        val utcRowsList   = runCatching { api.getUtcs().data     ?: emptyList<Map<String, Any?>>() }.getOrDefault(emptyList())

        val tipiList = tipiRawList
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull {
                buildLabel(
                    it.firstNonBlank("id")?.numToCleanString(),
                    it.firstNonBlank("descrizione", "nome", "label")
                )
            }
            .distinct().sorted()

        if (tipiList.isEmpty() && categorieList.isEmpty() && contiList.isEmpty() && utcRowsList.isEmpty()) {
            throw IOException("Tutte le chiamate API di lookup sono fallite (timeout o rete assente)")
        }

        // ── Scrittura su Room ─────────────────────────────────────────────
        lookupDao.clearTipi()
        lookupDao.upsertTipi(tipiList.map { TipoEntity(it) })
        lookupDao.clearCategorie()
        lookupDao.upsertCategorie(categorieList.map { CategoriaEntity(it) })
        lookupDao.clearConti()
        lookupDao.upsertConti(contiList.map { ContoEntity(value = it, utenteId = utenteId ?: "") })
        lookupDao.clearSottocategorie()
        lookupDao.upsertSottocategorie(sottoList)

        val tipologiaTipoMap = buildTipologiaTipoMap(tipiRawList)
        val utcEntities      = utcRowsList.toUtcEntities(tipologiaTipoMap)

        lookupDao.clearUtcs()
        lookupDao.upsertUtcs(utcEntities)
    }
}

// ── Parser privati condivisi ──────────────────────────────────────────────────
// Usati sia in syncAllBatch che in refreshLookupsFromRemoteAndSave per evitare duplicazioni.

/**
 * Costruisce la mappa tipologia-label → tipoMovimento dai raw rows delle tipologie.
 * Usata come lookup durante il parsing degli UTC.
 */
private fun buildTipologiaTipoMap(tipiRawList: List<Map<String, Any?>>): Map<String, String> =
    tipiRawList.associate { m ->
        val label = buildLabel(
            m.firstNonBlank("id")?.numToCleanString(),
            m.firstNonBlank("descrizione", "nome", "label")
        ) ?: ""
        label to (m.firstNonBlank("tipo_movimento", "TIPO_MOVIMENTO") ?: "uscita")
    }

/**
 * Converte raw rows di tipologie in [TipoEntity] complete (con attivo e tipoMovimento).
 * Usata da [SpeseRepository.syncAllBatch] dove i dati raw sono disponibili.
 */
private fun List<Map<String, Any?>>.toTipiEntitiesFull(): List<TipoEntity> =
    mapNotNull { m ->
        val label = buildLabel(
            m.firstNonBlank("id")?.numToCleanString(),
            m.firstNonBlank("descrizione", "nome", "label")
        ) ?: return@mapNotNull null
        TipoEntity(
            value         = label,
            attivo        = m.isActiveDefaultTrue(),
            tipoMovimento = m.firstNonBlank("tipo_movimento", "TIPO_MOVIMENTO") ?: "uscita"
        )
    }.distinct().sortedBy { it.value }

/**
 * Converte raw rows di categorie in [CategoriaEntity].
 */
private fun List<Map<String, Any?>>.toCategorieEntities(): List<CategoriaEntity> =
    mapNotNull { m ->
        val label = buildLabel(
            m.firstNonBlank("id")?.numToCleanString(),
            m.firstNonBlank("descrizione", "nome", "label")
        ) ?: return@mapNotNull null
        CategoriaEntity(value = label, attivo = m.isActiveDefaultTrue())
    }.distinct().sortedBy { it.value }

/**
 * Converte raw rows di sottocategorie in [SottoCategoriaEntity].
 */
private fun List<Map<String, Any?>>.toSottocategorieEntities(): List<SottoCategoriaEntity> =
    mapNotNull { m ->
        val catId = m.firstNonBlank("id_categoria", "categoria", "idCategoria")?.numToCleanString()
        val sub   = m.firstNonBlank("descrizione", "sottocategoria", "nome", "label")
        if (catId.isNullOrBlank() || sub.isNullOrBlank()) return@mapNotNull null
        SottoCategoriaEntity(
            key            = sottoKey(catId.trim(), sub.trim()),
            id             = m.firstNonBlank("id")?.numToCleanString()?.toIntOrNull() ?: 0,
            categoria      = catId.trim(),
            sottocategoria = sub.trim(),
            attivo         = m.isActiveDefaultTrue()
        )
    }.distinctBy { it.key }

/**
 * Converte raw rows di conti in [ContoEntity] per l'utente indicato.
 */
private fun List<Map<String, Any?>>.toContiEntities(utenteId: String): List<ContoEntity> =
    mapNotNull { m ->
        val label = m.firstNonBlank("id_conto", "ID_CONTO", "id_definizione_attivo")?.trim()
            ?: return@mapNotNull null
        ContoEntity(value = label, utenteId = utenteId, attivo = m.isActiveDefaultTrue())
    }.distinct()

/**
 * Converte raw rows UTC in [UtcEntity], usando [tipologiaTipoMap] per derivare tipoMovimento.
 */
private fun List<Map<String, Any?>>.toUtcEntities(
    tipologiaTipoMap: Map<String, String>
): List<UtcEntity> = mapNotNull { m ->
    val ut  = m.firstNonBlank("id_utente",        "utenza",         "ID_UTENTE")?.trim()
    val tip = m.firstNonBlank("id_tipologia",      "tipologia",      "ID_TIPOLOGIA")?.trim()
    val cat = m.firstNonBlank("id_categoria",      "categoria",      "ID_CATEGORIA")?.trim()
    val sub = m.firstNonBlank("id_sottocategoria", "sottocategoria", "ID_SOTTOCATEGORIA")?.trim()
    if (ut.isNullOrBlank() || tip.isNullOrBlank() ||
        cat.isNullOrBlank() || sub.isNullOrBlank()) return@mapNotNull null
    UtcEntity(
        key            = utcKey(ut, tip, cat, sub),
        utente         = ut,
        tipologia      = tip,
        categoria      = cat,
        sottocategoria = sub,
        attivo         = (m["attivo"] ?: m["ATTIVO"]).asBoolDefaultTrue(),
        tipoMovimento  = tipologiaTipoMap[tip] ?: "uscita"
    )
}

// ── Mapper privati ────────────────────────────────────────────────────────────

/**
 * Converte un [SpesaView] ricevuto dall'API in una [SpesaEntity] per Room.
 * Estrae mese e anno dalla stringa data nel formato `"YYYY-MM-DD"`.
 */
private fun SpesaView.toEntity(utente: String): SpesaEntity {
    val parts = data?.split("-")
    return SpesaEntity(
        id                 = id,
        utente             = utente,
        data               = data ?: "",
        importo            = importo,
        tipo               = tipo ?: "",
        tipoMovimento      = tipo_movimento,
        conto              = conto,
        contoDestinazione  = conto_destinazione,
        categoria          = categoria,
        sottocategoria     = sottocategoria,
        descrizione        = descrizione,
        mese               = parts?.getOrNull(1)?.toIntOrNull(),
        anno               = parts?.getOrNull(0)?.toIntOrNull()
    )
}

/** Converte una [SpesaEntity] da Room in un [SpesaView] usato dalla UI. */
private fun SpesaEntity.toSpesaView() = SpesaView(
    id                 = id,
    utente             = utente,
    data               = data,
    importo            = importo,
    tipo               = tipo,
    tipo_movimento     = tipoMovimento,
    conto              = conto,
    conto_destinazione = contoDestinazione,
    categoria          = categoria,
    sottocategoria     = sottocategoria,
    descrizione        = descrizione
)