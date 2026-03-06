package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lk_sottocategorie")
data class SottoCategoriaEntity(
    @PrimaryKey val key: String,
    val categoria: String,
    val sottocategoria: String
)