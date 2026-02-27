package com.emanuele.gestionespese.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emanuele.gestionespese.data.model.Categoria
import com.emanuele.gestionespese.data.model.RpcInsertSpesaRequest
import com.emanuele.gestionespese.data.model.Sottocategoria
import com.emanuele.gestionespese.data.model.SpesaUpsert
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.repo.SpeseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SpeseFilters(
    val query: String = "",
    val mese: String? = null,
    val tipo: String? = null,
    val metodo: String? = null
)

enum class FilterKey { MESE, TIPO, METODO, QUERY }

data class SpeseUiState(
    val loading: Boolean = false,
    val loadingLookups: Boolean = false,

    val saving: Boolean = false,

    val saveOkTick: Long = 0L,
    val draftPrefillTick: Long = 0L,

    val error: String? = null,

    val spese: List<SpesaView> = emptyList(),
    val filters: SpeseFilters = SpeseFilters(),

    val categorie: List<Categoria> = emptyList(),
    val sottocategorie: List<Sottocategoria> = emptyList(),

    // 👇 NUOVI CAMPI PREFILL
    val draftImporto: Double? = null,
    val draftDescrizione: String? = null,
    val draftData: String? = null,
    val draftMetodo: String? = null
)

class SpeseViewModel(private val repo: SpeseRepository) : ViewModel() {

    private val _state = MutableStateFlow(SpeseUiState())
    val state: StateFlow<SpeseUiState> = _state

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun consumeSaveOk() {
        _state.value = _state.value.copy(saveOkTick = 0L)
    }

    fun loadCategorie() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingLookups = true, error = null)
            runCatching { repo.listCategorie() }
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        loadingLookups = false,
                        categorie = list
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loadingLookups = false,
                        error = e.message
                    )
                }
        }
    }

    fun loadSottocategorie(categoriaId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loadingLookups = true,
                error = null,
                sottocategorie = emptyList()
            )
            runCatching { repo.listSottocategorie(categoriaId) }
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        loadingLookups = false,
                        sottocategorie = list
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loadingLookups = false,
                        error = e.message
                    )
                }
        }
    }

    fun prefillFromDraft(
        importo: Double,
        descrizione: String,
        dataMillis: Long,
        metodo: String
    ) {
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
            runCatching { repo.list() }
                .onSuccess { list ->
                    _state.value = _state.value.copy(loading = false, spese = list)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun saveSpesa(
        editingId: Int?,
        data: String,
        importo: Double,
        tipo: String,
        metodoPagamento: String,
        descrizione: String?,
        categoriaId: String,
        sottocategoriaId: String?
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)

            runCatching {
                val linkId = repo.resolveCategoriaLinkId(categoriaId, sottocategoriaId)

// calcolo mese/anno dalla data "YYYY-MM-DD"
                val anno = data.substring(0, 4).toInt()
                val mese = data.substring(5, 7).toInt()

                if (editingId == null) {
                    // INSERT via RPC: ID “primo disponibile” lo assegna il DB
                    val req = RpcInsertSpesaRequest(
                        data = data,
                        descrizione = descrizione,
                        importo = importo,
                        tipo = tipo,
                        mese = mese,
                        anno = anno,
                        metodoPagamento = metodoPagamento,
                        categoriaLinkId = linkId
                    )
                    repo.addViaRpc(req)
                } else {
                    // UPDATE classico: qui l’ID esiste già
                    val payload = SpesaUpsert(
                        id = editingId,
                        data = data,
                        importo = importo,
                        tipo = tipo,
                        mese = mese,
                        anno = anno,
                        categoriaLinkId = linkId,
                        metodoPagamento = metodoPagamento,
                        note = descrizione
                    )
                    repo.update(editingId, payload)
                }
            }
                .onSuccess { newList ->
                    _state.value = _state.value.copy(
                        saving = false,
                        saveOkTick = System.currentTimeMillis()
                    )
                }
                .onFailure { e ->
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
                repo.list()
            }
                .onSuccess { newList ->
                    _state.value = _state.value.copy(spese = newList)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message)
                }
        }
    }
}