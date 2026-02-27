package com.emanuele.gestionespese.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "spesa_draft",
    indices = [Index(value = ["dedupKey"], unique = true)]
)
data class SpesaDraftEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val amountCents: Long,
    val dateMillis: Long,

    val metodoPagamento: String,      // "Webank"

    val descrizione: String,          // merchant

    val categoriaId: String? = null,  // da compilare dopo
    val sottocategoriaId: String? = null,

    val status: String = "HOLD",

    val dedupKey: String
)