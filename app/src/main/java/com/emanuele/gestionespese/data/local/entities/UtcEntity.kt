package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "UTC_ENTITY")
data class UtcEntity(
    @PrimaryKey val key: String,     // utenza||tipologia||categoria||sottocategoria
    val utente: String,
    val tipologia: String,
    val categoria: String,
    val sottocategoria: String,
    val attivo: Boolean,
    val tipoMovimento: String? = null
)