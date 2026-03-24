package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Profilo di una banca configurata per il parsing delle notifiche.
 *
 * @property id                  Chiave primaria auto-generata.
 * @property displayName         Nome visualizzato (es. "Webank", "ING Direct").
 * @property packageName         Package Android dell'app bancaria (es. "com.opentecheng.android.webank").
 * @property isActive            Se false, le notifiche di questa banca vengono ignorate.
 * @property contentSource       Quale campo extras usare per il testo: TITLE, TEXT, BIG_TEXT, TEXT_OR_BIG.
 * @property wizardSampleText    Testo di notifica usato nel wizard per generare le regole (null = configurato manualmente).
 * @property wizardSelections    JSON delle selezioni del wizard (start, end, label) usate per rigenerare le regole.
 */
@Entity(
    tableName = "bank_profile",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class BankProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val packageName: String,
    val isActive: Boolean = true,
    val contentSource: String = "TEXT_OR_BIG", // TITLE | TEXT | BIG_TEXT | TEXT_OR_BIG
    val wizardSampleText: String? = null,
    val wizardSelections: String? = null       // JSON: [{"start":N,"end":M,"label":"IMPORTO"}, ...]
)
