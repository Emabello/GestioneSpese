package com.emanuele.gestionespese.data.local

import androidx.room.*
import com.emanuele.gestionespese.data.local.entities.DashboardEntity

@Dao
interface DashboardDao {

    @Query("SELECT * FROM dashboard WHERE utente = :utente LIMIT 1")
    suspend fun getByUtente(utente: String): DashboardEntity?

    @Upsert
    suspend fun upsert(entity: DashboardEntity)

    @Query("DELETE FROM dashboard WHERE utente = :utente")
    suspend fun deleteByUtente(utente: String)
}