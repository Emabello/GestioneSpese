package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.LookupDao
import com.emanuele.gestionespese.data.local.entities.CategoriaEntity
import com.emanuele.gestionespese.data.local.entities.ContoEntity
import com.emanuele.gestionespese.data.local.entities.SottoCategoriaEntity
import com.emanuele.gestionespese.data.local.entities.TipoEntity
import com.emanuele.gestionespese.data.local.entities.UtcEntity
import com.emanuele.gestionespese.data.local.sottoKey
import com.emanuele.gestionespese.data.local.utcKey
import com.emanuele.gestionespese.data.model.CategoriaRow
import com.emanuele.gestionespese.data.model.DeleteRequest
import com.emanuele.gestionespese.data.model.InsertRequest
import com.emanuele.gestionespese.data.model.SottoCatItem
import com.emanuele.gestionespese.data.model.SpesaPatch
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.UpdateRequest
import com.emanuele.gestionespese.data.model.UtcItem
import com.emanuele.gestionespese.data.remote.SupabaseApi

class SpeseRepository(
    private val api: SupabaseApi,
    private val lookupDao: LookupDao
) {

    suspend fun list(utente: String): List<SpesaView> =
        api.getSpese(utente = utente).data ?: emptyList()

    /** ===================== LOOKUPS (REMOTE) ===================== **/

    // 1) TIPOLOGIA
    suspend fun getTipi(): List<String> {
        val rows: List<Map<String, Any?>> = api.getTipi().data ?: emptyList()
        return rows
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull { m ->
                val id = m.firstNonBlank("id")?.numToCleanString()
                val descr = m.firstNonBlank("descrizione", "nome", "label")
                buildLabel(id, descr)
            }
            .distinct()
            .sorted()
    }

    // 2) CATEGORIA
    suspend fun getCategorie(): List<String> {
        val rows: List<CategoriaRow> = api.getCategorie().data ?: emptyList()
        return rows
            .filter { it.attiva.asBoolDefaultTrue() }
            .mapNotNull { r -> buildLabel(r.id.toString(), r.descrizione) }
            .distinct()
            .sorted()
    }

    // 3) SOTTOCATEGORIA (categoria = ID categoria)
    suspend fun getSottocategorie(): List<SottoCatItem> {
        val rows: List<Map<String, Any?>> = api.getSottocategorie().data ?: emptyList()

        return rows
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull { m ->
                val catId = m.firstNonBlank("id_categoria", "categoria", "idCategoria")?.numToCleanString()
                val sub = m.firstNonBlank("descrizione", "sottocategoria", "nome", "label")
                if (catId.isNullOrBlank() || sub.isNullOrBlank()) null
                else SottoCatItem(categoria = catId.trim(), sottocategoria = sub.trim())
            }
            .distinctBy { it.categoria.lowercase() + "||" + it.sottocategoria.lowercase() }
            .sortedWith(compareBy({ it.categoria.toIntOrNull() ?: Int.MAX_VALUE }, { it.sottocategoria.lowercase() }))
    }

    // 4) CONTO
    suspend fun getConti(utenteId: String? = null): List<String> {
        val rows: List<Map<String, Any?>> = api.getConti(utente = utenteId).data ?: emptyList()

        return rows
            .filter { it.isActiveDefaultTrue() }
            .mapNotNull { m ->
                val id = m.firstNonBlank("id")?.numToCleanString()
                val descr = m.firstNonBlank("descrizione", "nome", "label")
                buildLabel(id, descr)
            }
            .distinct()
            .sorted()
    }

    /** ===================== CRUD SPESE ===================== **/

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
    }

    suspend fun delete(id: Int) {
        val res = api.deleteSpesa(DeleteRequest(resource = "spese", id = id))
        if (res.error != null) throw IllegalStateException(res.error)
    }

    /** ===================== LOCAL CACHE (EXISTING) ===================== **/

    suspend fun getLookupsLocal(): LookupsLocal {
        val tipi = lookupDao.getTipi()
        val categorie = lookupDao.getCategorie()
        val conti = lookupDao.getConti("1 - E.BELLOTTI")
        val sottoPairs = lookupDao.getSottocategoriePairs()
        val sotto = sottoPairs.map { SottoCatItem(it.categoria, it.sottocategoria) }

        return LookupsLocal(
            tipi = tipi,
            categorie = categorie,
            conti = conti,
            sottocategorie = sotto
        )
    }

    suspend fun getUtcsFromDb(): List<UtcItem> {
        return lookupDao.getUtcs()
            .map {
                UtcItem(
                    utente = it.utente,
                    tipologia = it.tipologia,
                    categoria = it.categoria,
                    sottocategoria = it.sottocategoria,
                    attivo = it.attivo
                )
            }
    }

    suspend fun syncLookups(utenteId: String?) {
        val tipiRemote = getTipi()
        val categorieRemote = getCategorie()
        val sottoRemote = getSottocategorie()
        val contiRemote = getConti(utenteId)

        lookupDao.clearTipi()
        lookupDao.upsertTipi(tipiRemote.map { TipoEntity(it) })

        lookupDao.clearCategorie()
        lookupDao.upsertCategorie(categorieRemote.map { CategoriaEntity(it) })

        lookupDao.clearConti()
        lookupDao.upsertConti(
            contiRemote.map {
                ContoEntity(
                    value = it,
                    utenteId = (utenteId ?: "")
                )
            }
        )

        lookupDao.clearSottocategorie()
        val sottoEntities = sottoRemote.map {
            SottoCategoriaEntity(
                key = sottoKey(it.categoria, it.sottocategoria),
                categoria = it.categoria.trim(),
                sottocategoria = it.sottocategoria.trim()
            )
        }
        lookupDao.upsertSottocategorie(sottoEntities)
    }

    /** ===================== NEW: CACHE-FIRST LOOKUPS ===================== **/

    /**
     * Legge SOLO da Room (zero rete).
     * Se utenteId è null/blank prova prima a usare i conti "tutti" (se il tuo DAO li salva così),
     * altrimenti ritorna quello che trova.
     */
    suspend fun getLookupsFromDb(utenteId: String? = null): LookupsLocal {
        val tipi = lookupDao.getTipi()
        val categorie = lookupDao.getCategorie()

        val conti = when {
            !utenteId.isNullOrBlank() -> lookupDao.getConti(utenteId)
            else -> lookupDao.getConti("") // fallback: conti senza utente (se presenti)
        }

        val sottoPairs = lookupDao.getSottocategoriePairs()
        val sotto = sottoPairs.map { SottoCatItem(it.categoria, it.sottocategoria) }

        return LookupsLocal(
            tipi = tipi,
            categorie = categorie,
            sottocategorie = sotto,
            conti = conti
        )
    }

    /** ===================== REMOTE -> ROOM SYNC ===================== **/

    suspend fun refreshUtcsFromRemoteAndSave() {
        val rows: List<Map<String, Any?>> = api.getUtcs().data ?: emptyList()

        val items = rows.mapNotNull { m ->
            val utente = m.firstNonBlank("id_utente", "utente", "ID_UTENTE")?.trim()
            val tipologia = m.firstNonBlank("id_tipologia", "tipologia", "ID_TIPOLOGIA")?.trim()
            val categoria = m.firstNonBlank("id_categoria", "categoria", "ID_CATEGORIA")?.trim()
            val sottocategoria = m.firstNonBlank("id_sottocategoria", "sottocategoria", "ID_SOTTOCATEGORIA")?.trim()
            val attivo = (m["attivo"] ?: m["ATTIVO"]).asBoolDefaultTrue()

            if (utente.isNullOrBlank() || tipologia.isNullOrBlank() || categoria.isNullOrBlank() || sottocategoria.isNullOrBlank()) {
                null
            } else {
                UtcEntity(
                    key = utcKey(utente, tipologia, categoria, sottocategoria),
                    utente = utente,
                    tipologia = tipologia,
                    categoria = categoria,
                    sottocategoria = sottocategoria,
                    attivo = attivo
                )
            }
        }

        lookupDao.clearUtcs()
        lookupDao.upsertUtcs(items)
    }

    suspend fun refreshLookupsFromRemoteAndSave(utenteId: String? = null) {
        val tipiRemote = getTipi()
        val categorieRemote = getCategorie()
        val sottoRemote = getSottocategorie()
        val contiRemote = getConti(utenteId)

        // Tipi
        lookupDao.clearTipi()
        lookupDao.upsertTipi(tipiRemote.map { TipoEntity(it) })

        // Categorie
        lookupDao.clearCategorie()
        lookupDao.upsertCategorie(categorieRemote.map { CategoriaEntity(it) })

        // Conti
        lookupDao.clearConti()
        lookupDao.upsertConti(
            contiRemote.map {
                ContoEntity(
                    value = it,
                    utenteId = (utenteId ?: "")
                )
            }
        )

        // Sottocategorie
        lookupDao.clearSottocategorie()
        val sottoEntities = sottoRemote.map {
            SottoCategoriaEntity(
                key = sottoKey(it.categoria, it.sottocategoria),
                categoria = it.categoria.trim(),
                sottocategoria = it.sottocategoria.trim()
            )
        }
        lookupDao.upsertSottocategorie(sottoEntities)

        // UTCS
        refreshUtcsFromRemoteAndSave()
    }
}
/** ===================== Helpers Map ===================== **/

private fun Map<String, Any?>.firstNonBlank(vararg keys: String): String? {
    for (k in keys) {
        val v = this[k]
        val s = when (v) {
            null -> null
            is String -> v
            is Number -> v.toString()
            is Boolean -> v.toString()
            else -> v.toString()
        }?.trim()
        if (!s.isNullOrBlank()) return s
    }
    return null
}

private fun Map<String, Any?>.isActiveDefaultTrue(): Boolean {
    val raw = this["attivo"] ?: this["ATTIVO"] ?: this["attiva"] ?: this["ATTIVA"] ?: this["active"]
    return raw.asBoolDefaultTrue()
}

private fun Any?.asBoolDefaultTrue(): Boolean {
    return when (this) {
        null -> true
        is Boolean -> this
        is String -> this.equals("true", true) || this == "1" || this.equals("yes", true)
        is Number -> this.toInt() != 0
        else -> true
    }
}

// "3.0" -> "3"
private fun String.numToCleanString(): String {
    val s = this.trim()
    val d = s.toDoubleOrNull() ?: return s
    return if (d % 1.0 == 0.0) d.toInt().toString() else s
}

private fun buildLabel(id: String?, descr: String?): String? {
    val i = id?.trim().orEmpty()
    val d = descr?.trim().orEmpty()
    if (i.isBlank() && d.isBlank()) return null
    return if (i.isNotBlank() && d.isNotBlank()) "$i - $d" else (d.ifBlank { i })
}

data class LookupsLocal(
    val tipi: List<String>,
    val categorie: List<String>,
    val sottocategorie: List<SottoCatItem>,
    val conti: List<String>
)