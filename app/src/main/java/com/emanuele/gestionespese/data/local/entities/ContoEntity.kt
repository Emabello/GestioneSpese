/**
 * ContoEntity.kt
 *
 * Entità Room per la tabella di lookup dei conti correnti (`lk_conti`).
 * I conti sono per-utente: la chiave primaria composita è (utenteId, value).
 */
package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Conto corrente associato a uno specifico utente.
 *
 * @property utenteId Identificativo dell'utente proprietario del conto.
 * @property value    Nome del conto (es. `"Webank"`, `"Fineco"`).
 */
@Entity(
    tableName = "lk_conti",
    primaryKeys = ["utenteId", "value"],
    indices = [Index("utenteId")]
)
data class ContoEntity(
    val utenteId: String,
    val value: String
)
