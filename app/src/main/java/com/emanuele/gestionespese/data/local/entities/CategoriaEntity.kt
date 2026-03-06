package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lk_categorie")
data class CategoriaEntity(
    @PrimaryKey val value: String
)