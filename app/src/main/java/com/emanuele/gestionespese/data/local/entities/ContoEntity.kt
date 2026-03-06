package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "lk_conti",
    primaryKeys = ["utenteId", "value"],
    indices = [Index("utenteId")]
)
data class ContoEntity(
    val utenteId: String,
    val value: String
)