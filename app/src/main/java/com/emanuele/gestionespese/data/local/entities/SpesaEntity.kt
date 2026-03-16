package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spese")
data class SpesaEntity(
    @PrimaryKey val id: Int,
    val utente: String,
    val data: String,
    val importo: Double,
    val tipo: String,
    val tipoMovimento: String? = null,
    val conto: String?,
    val categoria: String?,
    val sottocategoria: String?,
    val descrizione: String?,
    val mese: Int?,
    val anno: Int?
)