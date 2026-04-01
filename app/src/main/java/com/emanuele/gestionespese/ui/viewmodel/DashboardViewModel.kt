/**
 * DashboardViewModel.kt
 *
 * ViewModel per la dashboard personalizzabile a griglia (6 colonne).
 * Gestisce il layout corrente dei widget e le operazioni di modifica:
 * aggiunta, rimozione, riordino, resize larghezza (colSpan) e altezza (heightStep).
 */
package com.emanuele.gestionespese.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.WidgetHeightStep
import com.emanuele.gestionespese.data.model.VALID_COL_SPANS
import com.emanuele.gestionespese.data.model.defaultDashboardLayout
import com.emanuele.gestionespese.data.model.minColSpan
import com.emanuele.gestionespese.data.repo.DashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val widgets: List<WidgetConfig> = defaultDashboardLayout(),
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false
)

class DashboardViewModel(
    private val repo: DashboardRepository,
    private val utente: String
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        loadLayout()
    }

    fun loadLayout() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val local = repo.getLayout(utente)
            _state.update { it.copy(widgets = local, isLoading = false) }
            if (utente.isNotBlank()) {
                runCatching { repo.syncFromRemote(utente) }
                    .onSuccess {
                        val fresh = repo.getLayout(utente)
                        _state.update { it.copy(widgets = fresh) }
                    }
            }
        }
    }

    fun toggleEditMode() {
        _state.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun saveLayout(widgets: List<WidgetConfig>) {
        viewModelScope.launch {
            val reordered = widgets.mapIndexed { i, w -> w.copy(position = i) }
            _state.update { it.copy(widgets = reordered) }
            repo.saveLayout(utente, reordered)
            repo.syncToRemote(utente, reordered)
        }
    }

    fun removeWidget(id: String) {
        saveLayout(_state.value.widgets.filter { it.id != id })
    }

    fun addWidget(widget: WidgetConfig) {
        saveLayout(_state.value.widgets + widget)
    }

    fun moveUp(id: String) {
        val list = _state.value.widgets.toMutableList()
        val idx  = list.indexOfFirst { it.id == id }
        if (idx > 0) {
            val tmp = list[idx - 1]; list[idx - 1] = list[idx]; list[idx] = tmp
            saveLayout(list)
        }
    }

    fun moveDown(id: String) {
        val list = _state.value.widgets.toMutableList()
        val idx  = list.indexOfFirst { it.id == id }
        if (idx < list.size - 1) {
            val tmp = list[idx + 1]; list[idx + 1] = list[idx]; list[idx] = tmp
            saveLayout(list)
        }
    }

    /**
     * Imposta il numero di colonne occupate dal widget (2 | 3 | 4 | 6).
     * Il valore viene clampato ai valori validi e al minColSpan del tipo.
     */
    fun setColSpan(id: String, cols: Int) {
        val snapped = VALID_COL_SPANS.minByOrNull { kotlin.math.abs(it - cols) } ?: cols
        val updated = _state.value.widgets.map { w ->
            if (w.id == id) w.copy(colSpan = snapped.coerceAtLeast(w.type.minColSpan()))
            else w
        }
        saveLayout(updated)
    }

    /**
     * Imposta lo step di altezza del widget (S / M / L).
     */
    fun setHeightStep(id: String, step: WidgetHeightStep) {
        val updated = _state.value.widgets.map { w ->
            if (w.id == id) w.copy(heightStep = step) else w
        }
        saveLayout(updated)
    }

    fun updateWidgetConfig(id: String, config: WidgetConfig) {
        saveLayout(_state.value.widgets.map { w -> if (w.id == id) config else w })
    }
}
