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

data class SpeseFilters(
    val query: String = "",
    val mese: String? = null,
    val tipo: String? = null,
    val metodo: String? = null
)

enum class FilterKey { MESE, TIPO, METODO, QUERY }

data class SpeseUiState(
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

    // filter
    val filters: SpeseFilters = SpeseFilters(),

    // ✅ LOOKUPS
    val tipi: List<String> = emptyList(),
    val categorie: List<String> = emptyList(),
    val sottocategorie: List<SottoCatItem> = emptyList(),
    val conti: List<String> = emptyList(),

    // draft
    val draftImporto: Double? = null,
    val draftDescrizione: String? = null,
    val draftData: String? = null,
    val draftMetodo: String? = null
)

class SpeseViewModel(private val repo: SpeseRepository) : ViewModel() {

    private val _state = MutableStateFlow(SpeseUiState())
    val state: StateFlow<SpeseUiState> = _state

    private val CURRENT_UTENTE = "2 - A.BERTOLI"

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun consumeSaveOk() {
        _state.value = _state.value.copy(saveOkTick = 0L)
    }

    fun prefillFromDraft(importo: Double, descrizione: String, dataMillis: Long, metodo: String) {
        val formattedDate = java.text.SimpleDateFormat(
            "yyyy-MM-dd",
            java.util.Locale.getDefault()
        ).format(java.util.Date(dataMillis))

        _state.value = _state.value.copy(
            draftImporto = importo,
            draftDescrizione = descrizione,
            draftData = formattedDate,
            draftMetodo = metodo,
            draftPrefillTick = System.currentTimeMillis()
        )
    }

    fun clearDraftPrefill() {
        _state.value = _state.value.copy(
            draftImporto = null,
            draftDescrizione = null,
            draftData = null,
            draftMetodo = null,
            draftPrefillTick = 0L
        )
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(query = q))
    }

    fun setMese(m: String?) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(mese = m))
    }

    fun setTipo(t: String?) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(tipo = t))
    }

    fun setMetodo(m: String?) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(metodo = m))
    }

    fun clearFilter(key: FilterKey) {
        val f = _state.value.filters
        _state.value = _state.value.copy(
            filters = when (key) {
                FilterKey.MESE -> f.copy(mese = null)
                FilterKey.TIPO -> f.copy(tipo = null)
                FilterKey.METODO -> f.copy(metodo = null)
                FilterKey.QUERY -> f.copy(query = "")
            }
        )
    }

    fun resetFilters() {
        _state.value = _state.value.copy(filters = SpeseFilters())
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repo.list(CURRENT_UTENTE) }
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        loading = false,
                        spese = list,
                        didLoadSpese = true
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun refreshIfNeeded(force: Boolean = false) {
        val st = _state.value
        if (force || (!st.didLoadSpese && !st.loading)) {
            refresh()
        }
    }

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
            _state.value = _state.value.copy(saving = true, error = null)

            runCatching {
                val utente = CURRENT_UTENTE

                if (editingId == null) {
                    repo.add(
                        utente = utente,
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
                        utente = utente,
                        data = data,
                        conto = conto,
                        importo = importo,
                        tipo = tipo,
                        categoria = categoria,
                        sottocategoria = sottocategoria,
                        descrizione = descrizione
                    )
                }

                repo.list(utente)
            }.onSuccess { newList ->
                _state.value = _state.value.copy(
                    saving = false,
                    spese = newList,
                    saveOkTick = System.currentTimeMillis(),
                    didLoadSpese = true
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    saving = false,
                    error = e.message
                )
            }
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            runCatching {
                repo.delete(id)
                repo.list(CURRENT_UTENTE)
            }.onSuccess { newList ->
                _state.value = _state.value.copy(
                    spese = newList,
                    didLoadSpese = true
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /**
     * ✅ LOOKUPS + UTCS: DB-first
     * - legge Room
     * - se mancano dati → chiama remoto e salva su Room
     * - rilegge Room e aggiorna UI
     */
    fun loadLookupsIfNeeded(force: Boolean = false) {
        val st = _state.value
        if (force || (!st.didLoadLookups && !st.loadingLookups)) {
            loadLookups()
        }
    }

    fun loadLookups() = viewModelScope.launch {
        _state.update { it.copy(loadingLookups = true, error = null) }

        try {
            // ✅ 1) Room first (veloce)
            val local = repo.getLookupsFromDb(utenteId = CURRENT_UTENTE)
            val utcsLocal = repo.getUtcsFromDb()

            val hasLocal =
                local.tipi.isNotEmpty() &&
                        local.categorie.isNotEmpty() &&
                        local.conti.isNotEmpty() &&
                        utcsLocal.isNotEmpty() // UTCS è fondamentale per filtri tipo/categoria/sottocategoria

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

            // ✅ 2) se Room è vuoto → remoto + save su Room
            repo.refreshLookupsFromRemoteAndSave(utenteId = CURRENT_UTENTE)
            repo.refreshUtcsFromRemoteAndSave()

            // ✅ 3) rileggi Room e aggiorna state
            val local2 = repo.getLookupsFromDb(utenteId = CURRENT_UTENTE)
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
            _state.update { it.copy(error = e.message ?: "Errore lookup") }
        } finally {
            _state.update { it.copy(loadingLookups = false) }
        }
    }
}