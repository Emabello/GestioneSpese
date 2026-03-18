/**
 * SpesaDraftEntity.kt
 *
 * Entità Room che rappresenta una bozza di spesa creata automaticamente
 * a partire da una notifica bancaria (es. Webank). Le bozze restano in stato
 * `"HOLD"` finché l'utente non le conferma o elimina dalla schermata Notifiche.
 *
 * La colonna [dedupKey] ha un indice UNIQUE per prevenire inserimenti duplicati
 * della stessa transazione (gestito tramite `INSERT OR IGNORE`).
 */
package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bozza di movimento bancario in attesa di validazione manuale.
 *
 * @property id              Chiave primaria auto-generata.
 * @property amountCents     Importo in centesimi di euro (es. 900 = 9,00 €).
 * @property dateMillis      Timestamp Unix in millisecondi della notifica.
 * @property metodoPagamento Sorgente della notifica (es. `"Webank"`).
 * @property descrizione     Merchant/descrizione estratta dalla notifica.
 * @property categoriaId     Categoria selezionata dall'utente (opzionale).
 * @property sottocategoriaId Sottocategoria selezionata dall'utente (opzionale).
 * @property status          Stato della bozza: `"HOLD"` in attesa, `"DONE"` confermata.
 * @property dedupKey        Chiave di deduplicazione SHA-256; impedisce doppioni.
 */
@Entity(
    tableName = "spesa_draft",
    indices = [Index(value = ["dedupKey"], unique = true)]
)
data class SpesaDraftEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val amountCents: Long,
    val dateMillis: Long,

    val metodoPagamento: String,

    val descrizione: String,

    val categoriaId: String? = null,
    val sottocategoriaId: String? = null,

    val status: String = "HOLD",

    val dedupKey: String
)
