package com.emanuele.gestionespese.data.local

import androidx.room.*
import com.emanuele.gestionespese.data.local.entities.SpesaEntity

@Dao
interface SpesaDao {

    @Query("SELECT * FROM spese WHERE utente = :utente ORDER BY data DESC")
    suspend fun getByUtente(utente: String): List<SpesaEntity>

    @Upsert
    suspend fun upsertAll(spese: List<SpesaEntity>)

    @Query("DELETE FROM spese WHERE utente = :utente")
    suspend fun clearByUtente(utente: String)

    @Query("DELETE FROM spese WHERE id = :id")
    suspend fun deleteById(id: Int)
}