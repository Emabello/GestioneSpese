package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Regola di parsing per un campo di una notifica bancaria.
 *
 * @property id            Chiave primaria auto-generata.
 * @property bankProfileId FK verso [BankProfileEntity]. CASCADE on delete.
 * @property field         Campo target: "AMOUNT", "MERCHANT" o "DATE".
 * @property regex         Espressione regolare grezza.
 * @property groupIndex    Indice del gruppo di cattura da usare (default 1).
 * @property priority      Ordine di prova ASC: regola con priority=0 viene provata prima.
 * @property description   Nota opzionale per l'utente (es. "Importo da ADDEBITO CARTA").
 *
 * Per AMOUNT: la regex può avere 2 gruppi (euro + centesimi) oppure 1 gruppo (valore decimale).
 * Per DATE:   la regex deve avere 5 gruppi: (dd)(MM)(yyyy)(HH)(mm).
 * Per MERCHANT: la regex deve avere 1 gruppo stringa.
 */
@Entity(
    tableName = "parse_rule",
    foreignKeys = [ForeignKey(
        entity = BankProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["bankProfileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bankProfileId")]
)
data class ParseRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankProfileId: Long,
    val field: String,
    val regex: String,
    val groupIndex: Int = 1,
    val priority: Int = 0,
    val description: String = ""
)
