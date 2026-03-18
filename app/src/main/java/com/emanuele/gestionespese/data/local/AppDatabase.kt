/**
 * AppDatabase.kt
 *
 * Database Room dell'applicazione. Contiene 8 tabelle:
 * - `spese` — movimenti sincronizzati dal backend
 * - `spesa_draft` — bozze da notifiche bancarie
 * - `lk_tipi`, `lk_categorie`, `lk_conti`, `lk_sottocategorie` — lookup tables
 * - `UTC_ENTITY` — associazioni utente-tipologia-categoria-sottocategoria
 * - `dashboard` — layout dashboard per utente
 *
 * Contiene anche le funzioni [sottoKey] e [utcKey] per generare le chiavi
 * composite usate come PrimaryKey in alcune entità.
 */
package com.emanuele.gestionespese.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.emanuele.gestionespese.data.local.entities.*

@Database(
    entities = [
        SpesaDraftEntity::class,
        TipoEntity::class,
        CategoriaEntity::class,
        ContoEntity::class,
        SottoCategoriaEntity::class,
        UtcEntity::class,
        SpesaEntity::class,
        DashboardEntity::class
    ],
    version = 10,
    exportSchema = false
)
/** Database principale dell'app, costruito tramite [androidx.room.Room.databaseBuilder] in [MyApp]. */
abstract class AppDatabase : RoomDatabase() {
    /** DAO per le bozze di movimenti bancari. */
    abstract fun spesaDraftDao(): SpesaDraftDao
    /** DAO per le tabelle di lookup (tipi, categorie, conti, sottocategorie, UTC). */
    abstract fun lookupDao(): LookupDao
    /** DAO per i movimenti sincronizzati. */
    abstract fun spesaDao(): SpesaDao
    /** DAO per il layout della dashboard. */
    abstract fun dashboardDao(): DashboardDao
}

// ── Chiavi composite usate nelle tabelle Room ─────────────────────────────────

/**
 * Genera la chiave primaria composita per [SottoCategoriaEntity].
 *
 * @param categoria    Nome della categoria (normalizzato a lowercase senza spazi).
 * @param sottocategoria Nome della sottocategoria.
 * @return Stringa nel formato `"categoria||sottocategoria"`.
 */
fun sottoKey(categoria: String, sottocategoria: String): String =
    "${categoria.trim().lowercase()}||${sottocategoria.trim().lowercase()}"

/**
 * Genera la chiave primaria composita per [UtcEntity]
 * (Utente–Tipologia–Categoria–Sottocategoria).
 *
 * @param utente         ID utente.
 * @param tipologia      Tipo di spesa.
 * @param categoria      Categoria della spesa.
 * @param sottocategoria Sottocategoria della spesa.
 * @return Stringa nel formato `"utente||tipologia||categoria||sottocategoria"`.
 */
fun utcKey(utente: String, tipologia: String, categoria: String, sottocategoria: String): String =
    "${utente.trim().lowercase()}||${tipologia.trim().lowercase()}||${categoria.trim().lowercase()}||${sottocategoria.trim().lowercase()}"