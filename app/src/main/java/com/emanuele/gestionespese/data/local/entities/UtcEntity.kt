/**
 * UtcEntity.kt
 *
 * Entità Room che memorizza le associazioni UTC
 * (Utente–Tipologia–Categoria–Sottocategoria) sincronizzate dal backend.
 * Serve per pre-compilare automaticamente la categoria/sottocategoria nel form
 * di aggiunta spesa, in base allo storico dell'utente.
 *
 * La chiave primaria [key] è generata da [utcKey].
 */
package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Associazione Utente–Tipologia–Categoria–Sottocategoria.
 *
 * @property key           Chiave composita generata da `utcKey(...)`.
 * @property utente        ID dell'utente.
 * @property tipologia     Tipo di movimento (es. `"Uscita"`).
 * @property categoria     Categoria associata.
 * @property sottocategoria Sottocategoria associata.
 * @property attivo        `false` se questa associazione è stata disattivata.
 * @property tipoMovimento Sotto-tipo di movimento opzionale (es. `"Carta"`).
 */
@Entity(tableName = "UTC_ENTITY")
data class UtcEntity(
    @PrimaryKey val key: String,
    val utente: String,
    val tipologia: String,
    val categoria: String,
    val sottocategoria: String,
    val attivo: Boolean,
    val tipoMovimento: String? = null
)
