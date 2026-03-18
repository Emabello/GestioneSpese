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

@Dao
interface LookupDao {

    // --- Tipi ---
    @Query("SELECT value FROM lk_tipi ORDER BY value")
    suspend fun getTipi(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTipi(items: List<TipoEntity>)

    @Query("DELETE FROM lk_tipi")
    suspend fun clearTipi()

    // --- Categorie ---
    @Query("SELECT value FROM lk_categorie ORDER BY value")
    suspend fun getCategorie(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategorie(items: List<CategoriaEntity>)

    @Query("DELETE FROM lk_categorie")
    suspend fun clearCategorie()

    // --- Conti ---
    @Query("SELECT value FROM lk_conti WHERE utenteId = :utenteId ORDER BY value")
    suspend fun getConti(utenteId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConti(items: List<ContoEntity>)

    @Query("DELETE FROM lk_conti")
    suspend fun clearConti()

    // --- Sottocategorie ---
    @Query("SELECT categoria, sottocategoria FROM lk_sottocategorie ORDER BY categoria, sottocategoria")
    suspend fun getSottocategoriePairs(): List<SottoCatPair>

    @Query("SELECT sottocategoria FROM lk_sottocategorie WHERE categoria = :categoria ORDER BY sottocategoria")
    suspend fun getSottocategorieByCategoria(categoria: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSottocategorie(items: List<SottoCategoriaEntity>)

    @Query("DELETE FROM lk_sottocategorie")
    suspend fun clearSottocategorie()

    // --- UTCs ---
    @Query("SELECT * FROM UTC_ENTITY")
    suspend fun getUtcs(): List<UtcEntity>

    @Query("SELECT * FROM UTC_ENTITY WHERE utente = :utente")
    suspend fun getUtcsByUtente(utente: String): List<UtcEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUtcs(items: List<UtcEntity>)

    @Query("DELETE FROM UTC_ENTITY")
    suspend fun clearUtcs()
}