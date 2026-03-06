package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lk_tipi")
data class TipoEntity(
    @PrimaryKey val value: String
)