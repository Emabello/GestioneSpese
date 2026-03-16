package com.emanuele.gestionespese.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.WidgetSize
import com.emanuele.gestionespese.data.model.defaultDashboardLayout
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
            val widgets = repo.getLayout(utente)
            _state.update { it.copy(widgets = widgets, isLoading = false) }
        }
    }

    fun toggleEditMode() {
        _state.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun saveLayout(widgets: List<WidgetConfig>) {
        viewModelScope.launch {
            val reordered = widgets.mapIndexed { i, w -> w.copy(position = i) }
            _state.update { it.copy(widgets = reordered, isEditMode = false) }
            repo.saveLayout(utente, reordered)
            repo.syncToRemote(utente, reordered)
        }
    }

    fun removeWidget(id: String) {
        val updated = _state.value.widgets.filter { it.id != id }
        saveLayout(updated)
    }

    fun addWidget(widget: WidgetConfig) {
        val updated = _state.value.widgets + widget
        saveLayout(updated)
    }

    fun moveUp(id: String) {
        val list = _state.value.widgets.toMutableList()
        val idx  = list.indexOfFirst { it.id == id }
        if (idx > 0) {
            val tmp = list[idx - 1]
            list[idx - 1] = list[idx]
            list[idx] = tmp
            saveLayout(list)
        }
    }

    fun moveDown(id: String) {
        val list = _state.value.widgets.toMutableList()
        val idx  = list.indexOfFirst { it.id == id }
        if (idx < list.size - 1) {
            val tmp = list[idx + 1]
            list[idx + 1] = list[idx]
            list[idx] = tmp
            saveLayout(list)
        }
    }

    fun toggleSize(id: String) {
        val updated = _state.value.widgets.map { w ->
            if (w.id == id) w.copy(
                size = if (w.size == WidgetSize.WIDE) WidgetSize.SMALL else WidgetSize.WIDE
            ) else w
        }
        saveLayout(updated)
    }
}