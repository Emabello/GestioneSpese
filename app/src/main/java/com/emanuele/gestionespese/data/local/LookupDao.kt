/**
 * LookupDao.kt
 *
 * DAO Room per le tabelle di lookup dell'app (tipi, categorie, conti, sottocategorie, UTC).
 * Contiene anche [SottoCatPair], data class usata come proiezione nelle query.
 * Ogni sezione espone operazioni di lettura, upsert e clear per consentire
 * una sostituzione completa dei dati durante la sincronizzazione.
 */
package com.emanuele.gestionespese.data.local

import androidx.room.*
import com.emanuele.gestionespese.data.local.entities.*

/**
 * Coppia (categoria, sottocategoria) usata come proiezione nelle query Room
 * per [LookupDao.getSottocategoriePairs].
 *
 * @property categoria     Nome della categoria padre.
 * @property sottocategoria Nome della sottocategoria figlia.
 */
data class SottoCatPair(
    val categoria: String,
    val sottocategoria: String
)

/** Data Access Object per le tabelle di lookup sincronizzate dal backend. */
@Dao
interface LookupDao {

    // ── Tipi ─────────────────────────────────────────────────────────────────

    /** Restituisce tutti i tipi di movimento in ordine alfabetico. */
    @Query("SELECT value FROM lk_tipi WHERE attivo = 1 ORDER BY value")
    suspend fun getTipi(): List<String>

    // Tipi — tutti (per ConfigScreen)
    @Query("SELECT value, attivo, tipoMovimento FROM lk_tipi ORDER BY value")
    suspend fun getTipiRaw(): List<TipoEntity>

    /** Inserisce o aggiorna una lista di tipi. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTipi(items: List<TipoEntity>)

    /** Svuota la tabella dei tipi. */
    @Query("DELETE FROM lk_tipi")
    suspend fun clearTipi()

    // ── Categorie ────────────────────────────────────────────────────────────

    /** Restituisce tutte le categorie in ordine alfabetico. */
    @Query("SELECT value FROM lk_categorie WHERE attivo = 1 ORDER BY value")
    suspend fun getCategorie(): List<String>

    // Categorie — tutte
    @Query("SELECT value, attivo FROM lk_categorie ORDER BY value")
    suspend fun getCategorieRaw(): List<CategoriaEntity>

    /** Inserisce o aggiorna una lista di categorie. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategorie(items: List<CategoriaEntity>)

    /** Svuota la tabella delle categorie. */
    @Query("DELETE FROM lk_categorie")
    suspend fun clearCategorie()

    // ── Conti ─────────────────────────────────────────────────────────────────

    /**
     * Restituisce i conti di un utente in ordine alfabetico.
     *
     * @param utenteId ID dell'utente.
     */
    @Query("SELECT value FROM lk_conti WHERE utenteId = :utenteId AND attivo = 1 ORDER BY value")
    suspend fun getConti(utenteId: String): List<String>

    // Conti — tutti
    @Query("SELECT * FROM lk_conti WHERE utenteId = :utenteId ORDER BY value")
    suspend fun getContiRaw(utenteId: String): List<ContoEntity>

    /** Inserisce o aggiorna una lista di conti. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConti(items: List<ContoEntity>)

    /** Svuota la tabella dei conti (di tutti gli utenti). */
    @Query("DELETE FROM lk_conti")
    suspend fun clearConti()

    // ── Sottocategorie ───────────────────────────────────────────────────────

    /** Restituisce tutte le coppie (categoria, sottocategoria) ordinate. */
    @Query("SELECT categoria, sottocategoria FROM lk_sottocategorie WHERE attivo = 1  ORDER BY categoria, sottocategoria")
    suspend fun getSottocategoriePairs(): List<SottoCatPair>

    // Sottocategorie — tutte
    @Query("SELECT * FROM lk_sottocategorie ORDER BY categoria, sottocategoria")
    suspend fun getSottocategorieRaw(): List<SottoCategoriaEntity>

    /**
     * Restituisce le sottocategorie di una categoria specifica.
     *
     * @param categoria Nome della categoria padre.
     */
    @Query("SELECT sottocategoria FROM lk_sottocategorie WHERE categoria = :categoria ORDER BY sottocategoria")
    suspend fun getSottocategorieByCategoria(categoria: String): List<String>

    /** Inserisce o aggiorna una lista di sottocategorie. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSottocategorie(items: List<SottoCategoriaEntity>)

    /** Svuota la tabella delle sottocategorie. */
    @Query("DELETE FROM lk_sottocategorie")
    suspend fun clearSottocategorie()

    // ── UTC (Utente-Tipologia-Categoria-Sottocategoria) ──────────────────────

    /** Restituisce tutte le associazioni UTC. */
    @Query("SELECT * FROM UTC_ENTITY")
    suspend fun getUtcs(): List<UtcEntity>

    /**
     * Restituisce le associazioni UTC di uno specifico utente.
     *
     * @param utente ID dell'utente.
     */
    @Query("SELECT * FROM UTC_ENTITY WHERE utente = :utente")
    suspend fun getUtcsByUtente(utente: String): List<UtcEntity>

    /** Inserisce o aggiorna una lista di associazioni UTC. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUtcs(items: List<UtcEntity>)

    /** Svuota la tabella UTC. */
    @Query("DELETE FROM UTC_ENTITY")
    suspend fun clearUtcs()
}