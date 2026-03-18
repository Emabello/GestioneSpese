/**
 * SpesaDraftDao.kt
 *
 * DAO Room per la tabella `spesa_draft`. Gestisce le bozze di movimenti create
 * automaticamente dal listener di notifiche bancarie. Espone un [Flow] reattivo
 * per osservare le bozze in tempo reale dalla UI.
 */
package com.emanuele.gestionespese.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object per le bozze di movimenti bancari. */
@Dao
interface SpesaDraftDao {

    /**
     * Inserisce una bozza, sovrascrivendo in caso di conflitto sulla chiave.
     *
     * @param entity La bozza da inserire.
     * @return ID autogenerato della riga inserita.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpesaDraftEntity): Long

    /**
     * Osserva in tempo reale le bozze con un determinato stato.
     *
     * @param status Stato delle bozze da osservare (default `"HOLD"`).
     * @return [Flow] aggiornato ad ogni modifica della tabella.
     */
    @Query("SELECT * FROM spesa_draft WHERE status = :status ORDER BY dateMillis DESC")
    fun observeByStatus(status: String = "HOLD"): Flow<List<SpesaDraftEntity>>

    /**
     * Aggiorna lo stato di una bozza (es. da `"HOLD"` a `"DONE"`).
     *
     * @param id        ID della bozza da aggiornare.
     * @param newStatus Nuovo stato da impostare.
     * @return Numero di righe modificate (0 se la bozza non esiste).
     */
    @Query("UPDATE spesa_draft SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String): Int

    /**
     * Elimina una bozza per ID.
     *
     * @param id ID della bozza da eliminare.
     * @return Numero di righe eliminate.
     */
    @Query("DELETE FROM spesa_draft WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * Inserisce una bozza solo se non esiste già una con lo stesso [dedupKey].
     * Usato da [BankNotificationListener] per evitare duplicati.
     *
     * @param entity La bozza da inserire.
     * @return ID della riga inserita, o `-1` se ignorata per deduplicazione.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: SpesaDraftEntity): Long
}
