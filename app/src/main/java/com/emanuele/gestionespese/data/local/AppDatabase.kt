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
abstract class AppDatabase : RoomDatabase() {
    abstract fun spesaDraftDao(): SpesaDraftDao
    abstract fun lookupDao(): LookupDao
    abstract fun spesaDao(): SpesaDao
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