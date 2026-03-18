/**
 * SpesaDraftRepository.kt
 *
 * Repository per le bozze di movimenti bancari. Funge da intermediario tra
 * [DraftsViewModel] e [SpesaDraftDao], esponendo le operazioni essenziali
 * per osservare, eliminare e inserire bozze.
 */
package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.SpesaDraftDao
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import kotlinx.coroutines.flow.Flow

/**
 * Gestisce le operazioni sulle bozze di movimenti bancari.
 *
 * @param dao DAO Room per la tabella `spesa_draft`.
 */
class SpesaDraftRepository(
    private val dao: SpesaDraftDao
) {
    /**
     * Osserva in tempo reale le bozze in stato `"HOLD"` (in attesa di validazione).
     *
     * @return [Flow] aggiornato ad ogni modifica della tabella.
     */
    fun observeHold(): Flow<List<SpesaDraftEntity>> = dao.observeByStatus("HOLD")

    /**
     * Elimina definitivamente una bozza per ID.
     *
     * @param id ID della bozza da eliminare.
     */
    suspend fun delete(id: Long) { dao.deleteById(id) }

    /**
     * Inserisce una bozza di test (usata da [DraftsViewModel.insertFakeDraft] in sviluppo).
     *
     * @param entity La bozza da inserire.
     * @return ID autogenerato della riga inserita.
     */
    suspend fun insertTest(entity: SpesaDraftEntity): Long = dao.insert(entity)
}