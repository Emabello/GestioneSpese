/**
 * SottoCategoriaEntity.kt
 *
 * Entità Room per la tabella di lookup delle sottocategorie (`lk_sottocategorie`).
 * La chiave primaria [key] è generata da [sottoKey] per garantire l'unicità
 * della coppia (categoria, sottocategoria).
 */
package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sottocategoria di spesa associata a una categoria padre.
 *
 * @property key           Chiave composita `"categoria||sottocategoria"` (vedi `sottoKey`).
 * @property categoria     Nome della categoria padre.
 * @property sottocategoria Nome della sottocategoria.
 */
@Entity(tableName = "lk_sottocategorie")
data class SottoCategoriaEntity(
    @PrimaryKey val key: String,
    val id: Int = 0,
    val categoria: String,
    val sottocategoria: String,
    val attivo: Boolean = true
)
