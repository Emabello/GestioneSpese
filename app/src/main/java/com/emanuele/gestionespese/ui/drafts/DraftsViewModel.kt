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

class DraftsViewModel(private val repo: SpesaDraftRepository) : ViewModel() {

    val drafts: StateFlow<List<SpesaDraftEntity>> =
        repo.observeHold().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

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