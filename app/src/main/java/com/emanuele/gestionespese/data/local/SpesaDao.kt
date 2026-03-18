/**
 * SpesaDao.kt
 *
 * DAO Room per la tabella `spese`. Gestisce le operazioni CRUD sui movimenti
 * sincronizzati dal backend. Tutti i metodi sono `suspend` e devono essere
 * chiamati da una coroutine.
 */
package com.emanuele.gestionespese.data.local

import androidx.room.*
import com.emanuele.gestionespese.data.local.entities.SpesaEntity

/** Data Access Object per i movimenti finanziari dell'utente. */
@Dao
interface SpesaDao {

    /**
     * Restituisce tutte le spese di un utente ordinate per data decrescente.
     *
     * @param utente Identificativo dell'utente.
     * @return Lista di [SpesaEntity] ordinata dalla più recente.
     */
    @Query("SELECT * FROM spese WHERE utente = :utente ORDER BY data DESC")
    suspend fun getByUtente(utente: String): List<SpesaEntity>

    /**
     * Inserisce o aggiorna (upsert) una lista di movimenti.
     * Usato durante la sincronizzazione per allineare il DB locale al backend.
     *
     * @param spese Lista di movimenti da inserire/aggiornare.
     */
    @Upsert
    suspend fun upsertAll(spese: List<SpesaEntity>)

    /**
     * Elimina tutti i movimenti di un utente (es. prima di un re-sync completo).
     *
     * @param utente Identificativo dell'utente.
     */
    @Query("DELETE FROM spese WHERE utente = :utente")
    suspend fun clearByUtente(utente: String)

    /**
     * Elimina un singolo movimento per ID.
     *
     * @param id ID del movimento da eliminare.
     */
    @Query("DELETE FROM spese WHERE id = :id")
    suspend fun deleteById(id: Int)
}
