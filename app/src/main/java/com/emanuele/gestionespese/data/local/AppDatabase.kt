/**
 * AppDatabase.kt
 *
 * Database Room dell'applicazione. Contiene 10 tabelle:
 * - `spese` — movimenti sincronizzati dal backend
 * - `spesa_draft` — bozze da notifiche bancarie
 * - `lk_tipi`, `lk_categorie`, `lk_conti`, `lk_sottocategorie` — lookup tables
 * - `UTC_ENTITY` — associazioni utente-tipologia-categoria-sottocategoria
 * - `dashboard` — layout dashboard per utente
 * - `bank_profile` — profili banche configurate per il parsing notifiche
 * - `parse_rule` — regole regex per ogni campo di ogni profilo bancario
 *
 * Contiene anche le funzioni [sottoKey] e [utcKey] per generare le chiavi
 * composite usate come PrimaryKey in alcune entità.
 */
package com.emanuele.gestionespese.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        DashboardEntity::class,
        BankProfileEntity::class,
        ParseRuleEntity::class
    ],
    version = 16,
    exportSchema = false,
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
    /** DAO per i profili bancari configurabili e le relative regole di parsing. */
    abstract fun bankProfileDao(): BankProfileDao
}

/** Migration 15→16: aggiunge colonna contoDestinazione a spese (per trasferimenti tra conti). */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE spese ADD COLUMN contoDestinazione TEXT")
    }
}

/** Migration 14→15: aggiunge campi wizard (wizardSampleText, wizardSelections) a bank_profile. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE bank_profile ADD COLUMN wizardSampleText TEXT")
        database.execSQL("ALTER TABLE bank_profile ADD COLUMN wizardSelections TEXT")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Ricrea bank_profile con lo schema corretto (no DEFAULT, indice unique corretto)
        database.execSQL("DROP TABLE IF EXISTS bank_profile_old")
        database.execSQL("ALTER TABLE bank_profile RENAME TO bank_profile_old")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS bank_profile (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                displayName TEXT NOT NULL,
                packageName TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                contentSource TEXT NOT NULL
            )
        """.trimIndent())
        database.execSQL("""
            INSERT INTO bank_profile (id, displayName, packageName, isActive, contentSource)
            SELECT id, displayName, packageName, isActive, contentSource
            FROM bank_profile_old
        """.trimIndent())
        database.execSQL("DROP TABLE IF EXISTS bank_profile_old")
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_bank_profile_packageName ON bank_profile(packageName)"
        )
    }
}

/** Migration 12→13: aggiunge le tabelle bank_profile e parse_rule. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS bank_profile (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                displayName TEXT NOT NULL,
                packageName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                contentSource TEXT NOT NULL DEFAULT 'TEXT_OR_BIG',
                UNIQUE(packageName)
            )
        """.trimIndent())

        database.execSQL("""
            CREATE TABLE IF NOT EXISTS parse_rule (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bankProfileId INTEGER NOT NULL,
                field TEXT NOT NULL,
                regex TEXT NOT NULL,
                groupIndex INTEGER NOT NULL DEFAULT 1,
                priority INTEGER NOT NULL DEFAULT 0,
                description TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(bankProfileId) REFERENCES bank_profile(id) ON DELETE CASCADE
            )
        """.trimIndent())

        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_parse_rule_bankProfileId ON parse_rule(bankProfileId)"
        )
    }
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