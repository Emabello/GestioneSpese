/**
 * CategoriaEntity.kt
 *
 * Entità Room per la tabella di lookup delle categorie di spesa (`lk_categorie`).
 * Viene popolata e aggiornata durante la sincronizzazione con il backend.
 */
package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Categoria di spesa (es. `"Alimentari"`, `"Trasporti"`, `"Salute"`).
 *
 * @property value Nome della categoria, usato come chiave primaria.
 */
@Entity(tableName = "lk_categorie")
data class CategoriaEntity(
    @PrimaryKey val value: String
)
