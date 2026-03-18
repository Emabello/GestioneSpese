/**
 * DashboardDao.kt
 *
 * DAO Room per la tabella `dashboard`. Gestisce la persistenza locale del
 * layout personalizzato della dashboard (un record per utente).
 */
package com.emanuele.gestionespese.data.local

import androidx.room.*
import com.emanuele.gestionespese.data.local.entities.DashboardEntity

/** Data Access Object per il layout della dashboard utente. */
@Dao
interface DashboardDao {

    /**
     * Recupera il layout della dashboard per un utente.
     *
     * @param utente Identificativo dell'utente.
     * @return [DashboardEntity] con il layout JSON, o `null` se non ancora salvato.
     */
    @Query("SELECT * FROM dashboard WHERE utente = :utente LIMIT 1")
    suspend fun getByUtente(utente: String): DashboardEntity?

    /**
     * Inserisce o aggiorna il layout della dashboard.
     *
     * @param entity Il layout da salvare.
     */
    @Upsert
    suspend fun upsert(entity: DashboardEntity)

    /**
     * Elimina il layout della dashboard di un utente (es. al logout).
     *
     * @param utente Identificativo dell'utente.
     */
    @Query("DELETE FROM dashboard WHERE utente = :utente")
    suspend fun deleteByUtente(utente: String)
}
