package com.emanuele.gestionespese.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emanuele.gestionespese.data.model.SottoCatItem
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.UtcItem
import com.emanuele.gestionespese.data.repo.SpeseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpeseViewModel(private val repo: SpeseRepository, private val currentUtente: String  ) : ViewModel() {

    private val _state = MutableStateFlow(SpeseUiState())

    val state: StateFlow<SpeseUiState> = _state

    fun clearError() = _state.update { it.copy(error = null) }

    fun consumeSaveOk() = _state.update { it.copy(saveOkTick = 0L) }

    fun prefillFromDraft(importo: Double, descrizione: String, dataMillis: Long, metodo: String) {
        val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(dataMillis))
        _state.update {
            it.copy(
                draftImporto = importo,
                draftDescrizione = descrizione,
                draftData = formattedDate,
                draftMetodo = metodo,
                draftPrefillTick = System.currentTimeMillis()
            )
        }
    }

    fun clearDraftPrefill() = _state.update {
        it.copy(
            draftImporto = null,
            draftDescrizione = null,
            draftData = null,
            draftMetodo = null,
            draftPrefillTick = 0L
        )
    }

    // --- Filtri ---

    fun setQuery(q: String) = _state.update { it.copy(filters = it.filters.copy(query = q)) }
    fun setMese(m: String?) = _state.update { it.copy(filters = it.filters.copy(mese = m)) }
    fun setTipo(t: String?) = _state.update { it.copy(filters = it.filters.copy(tipo = t)) }
    fun setMetodo(m: String?) = _state.update { it.copy(filters = it.filters.copy(metodo = m)) }

    fun clearFilter(key: FilterKey) = _state.update {
        it.copy(
            filters = when (key) {
                FilterKey.MESE -> it.filters.copy(mese = null)
                FilterKey.TIPO -> it.filters.copy(tipo = null)
                FilterKey.METODO -> it.filters.copy(metodo = null)
                FilterKey.QUERY -> it.filters.copy(query = "")
            }
        )
    }

    fun resetFilters() = _state.update { it.copy(filters = SpeseFilters()) }

    // --- Spese ---

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repo.list(currentUtente) }
                .onSuccess { list ->
                    _state.update { it.copy(loading = false, spese = list, didLoadSpese = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun refreshIfNeeded(force: Boolean = false) {
        val st = _state.value
        if (force || (!st.didLoadSpese && !st.loading)) refresh()
    }

    /**
     * Salva o aggiorna una spesa.
     * @param editingId null oppure -1 = nuova spesa; qualsiasi altro valore = modifica esistente
     */
    fun saveSpesa(
        editingId: Int?,
        data: String,
        importo: Double,
        tipo: String,
        conto: String,
        descrizione: String?,
        categoria: String,
        sottocategoria: String?
    ) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                val isNew = editingId == null || editingId == -1
                if (isNew) {
                    repo.add(
                        utente = currentUtente,
                        data = data,
                        conto = conto,
                        importo = importo,
                        tipo = tipo,
                        categoria = categoria,
                        sottocategoria = sottocategoria,
                        descrizione = descrizione
                    )
                } else {
                    repo.update(
                        id = editingId,
                        utente = currentUtente,
                        data = data,
                        conto = conto,
                        importo = importo,
                        tipo = tipo,
                        categoria = categoria,
                        sottocategoria = sottocategoria,
                        descrizione = descrizione
                    )
                }
                repo.list(currentUtente)
            }.onSuccess { newList ->
                _state.update {
                    it.copy(
                        saving = false,
                        spese = newList,
                        saveOkTick = System.currentTimeMillis(),
                        didLoadSpese = true
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    fun loadLookupsIfNeeded(force: Boolean = false) {
        val st = _state.value
        if (force || (!st.didLoadLookups && !st.loadingLookups)) loadLookups()
    }

    fun loadLookups() = viewModelScope.launch {
        _state.update { it.copy(loadingLookups = true, error = null) }
        try {
            val local = repo.getLookupsFromDb(utenteId = currentUtente)
            val utcsLocal = repo.getUtcsFromDb()

            val hasLocal = local.tipi.isNotEmpty()
                    && local.categorie.isNotEmpty()
                    && local.conti.isNotEmpty()
                    && utcsLocal.isNotEmpty()

            if (hasLocal) {
                _state.update {
                    it.copy(
                        tipi = local.tipi,
                        categorie = local.categorie,
                        conti = local.conti,
                        sottocategorie = local.sottocategorie,
                        utcs = utcsLocal,
                        didLoadLookups = true
                    )
                }
                return@launch
            }

            // Room vuoto → sync remoto (include anche UTCs)
            repo.refreshLookupsFromRemoteAndSave(utenteId = currentUtente)

            val local2 = repo.getLookupsFromDb(utenteId = currentUtente)
            val utcsLocal2 = repo.getUtcsFromDb()

            _state.update {
                it.copy(
                    tipi = local2.tipi,
                    categorie = local2.categorie,
                    conti = local2.conti,
                    sottocategorie = local2.sottocategorie,
                    utcs = utcsLocal2,
                    didLoadLookups = true
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Errore caricamento lookup") }
        } finally {
            _state.update { it.copy(loadingLookups = false) }
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            runCatching { repo.delete(id = id, utente = currentUtente) }
                .onSuccess {
                    _state.update { it.copy(spese = it.spese.filter { s -> s.id != id }) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadingLookups = true, error = null, syncDone = false) }
            runCatching { repo.syncAll(currentUtente) }
                .onSuccess {
                    val spese = repo.list(currentUtente)
                    val local = repo.getLookupsFromDb(utenteId = currentUtente)
                    val utcs  = repo.getUtcsFromDb()
                    _state.update {
                        it.copy(
                            loading = false, loadingLookups = false,
                            spese = spese, didLoadSpese = true,
                            tipi = local.tipi, categorie = local.categorie,
                            conti = local.conti, sottocategorie = local.sottocategorie,
                            utcs = utcs, didLoadLookups = true,
                            syncDone = true   // ← sblocca la UI
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false, loadingLookups = false,
                            error = e.message,
                            syncDone = true   // ← mostra comunque la UI con errore
                        )
                    }
                }
        }
    }
}



data class SpeseFilters(
    val query: String = "",
    val mese: String? = null,
    val tipo: String? = null,
    val metodo: String? = null
)

enum class FilterKey { MESE, TIPO, METODO, QUERY }

data class SpeseUiState(
    val syncDone: Boolean = false,
    val didLoadSpese: Boolean = false,
    val didLoadLookups: Boolean = false,
    val loading: Boolean = false,
    val loadingLookups: Boolean = false,
    val saving: Boolean = false,
    val saveOkTick: Long = 0L,
    val draftPrefillTick: Long = 0L,
    val error: String? = null,

    val spese: List<SpesaView> = emptyList(),
    val utcs: List<UtcItem> = emptyList(),

    val filters: SpeseFilters = SpeseFilters(),

    val tipi: List<String> = emptyList(),
    val categorie: List<String> = emptyList(),
    val sottocategorie: List<SottoCatItem> = emptyList(),
    val conti: List<String> = emptyList(),

    val draftImporto: Double? = null,
    val draftDescrizione: String? = null,
    val draftData: String? = null,
    val draftMetodo: String? = null
)
