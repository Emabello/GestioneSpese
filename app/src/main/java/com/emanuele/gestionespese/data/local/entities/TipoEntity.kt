/**
 * TipoEntity.kt
 *
 * Entità Room per la tabella di lookup dei tipi di movimento (`lk_tipi`).
 * Viene popolata e aggiornata durante la sincronizzazione con il backend.
 */
package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tipo di movimento (es. `"Uscita"`, `"Entrata"`, `"Giroconto"`).
 *
 * @property value Nome del tipo, usato come chiave primaria.
 */
@Entity(tableName = "lk_tipi")
data class TipoEntity(
    @PrimaryKey val value: String
)
