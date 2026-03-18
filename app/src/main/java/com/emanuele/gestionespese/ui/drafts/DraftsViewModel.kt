/**
 * DraftsViewModel.kt
 *
 * ViewModel per la schermata delle bozze di movimenti bancari (notifiche Webank).
 * Osserva in tempo reale le bozze in stato `"HOLD"` tramite [SpesaDraftRepository]
 * e fornisce operazioni di eliminazione e inserimento di test.
 *
 * Contiene anche la [factory] companion per la creazione tramite [ViewModelProvider].
 */
package com.emanuele.gestionespese.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import com.emanuele.gestionespese.data.repo.SpesaDraftRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/** ViewModel per la lista delle bozze di movimenti bancari in attesa di conferma. */
class DraftsViewModel(private val repo: SpesaDraftRepository) : ViewModel() {

    /** Flusso reattivo delle bozze in stato `"HOLD"`, aggiornato in tempo reale. */
    val drafts: StateFlow<List<SpesaDraftEntity>> =
        repo.observeHold().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Elimina definitivamente una bozza per ID.
     *
     * @param id ID della bozza da eliminare.
     */
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

    /**
     * Inserisce una bozza di test con dati casuali.
     * Usato durante lo sviluppo per testare la UI senza notifiche bancarie reali.
     */
    fun insertFakeDraft() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val entity = SpesaDraftEntity(
            amountCents = listOf(900L, 2450L, 1299L, 560L, 3100L).random(),
            dateMillis = now,
            metodoPagamento = "",
            descrizione = listOf(
                "NEW MANGAL KEBAB DI T..(,MILANO)",
                "ESSELUNGA MILANO",
                "AMAZON EU SARL",
                "TRENORD BIGLIETTO",
                "BAR CENTRALE"
            ).random(),
            categoriaId = null,
            sottocategoriaId = null,
            status = "HOLD",
            dedupKey = "TEST-$now-${Random.nextInt(0, 1_000_000)}"
        )
        repo.insertTest(entity)
    }

    companion object {
        /** Factory per creare [DraftsViewModel] con il repository necessario. */
        fun factory(repo: SpesaDraftRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DraftsViewModel(repo) as T
            }
    }
}