/**
 * DashboardViewModel.kt
 *
 * ViewModel per la dashboard personalizzabile. Gestisce il layout corrente
 * dei widget e le operazioni di modifica (aggiunta, rimozione, riordino,
 * cambio dimensione). Persiste le modifiche tramite [DashboardRepository].
 */
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

/**
 * Stato UI della dashboard.
 *
 * @property widgets    Lista ordinata dei widget da visualizzare.
 * @property isLoading  `true` durante il caricamento del layout.
 * @property isEditMode `true` quando la modalità di modifica è attiva.
 */
data class DashboardUiState(
    val widgets: List<WidgetConfig> = defaultDashboardLayout(),
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false
)

/**
 * ViewModel per la gestione del layout della dashboard.
 *
 * @param repo   Repository per la persistenza locale e la sync remota.
 * @param utente ID dell'utente corrente.
 */
class DashboardViewModel(
    private val repo: DashboardRepository,
    private val utente: String
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        loadLayout()
    }

    /** Carica il layout dal repository e aggiorna lo stato. */
    fun loadLayout() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val widgets = repo.getLayout(utente)
            _state.update { it.copy(widgets = widgets, isLoading = false) }
        }
    }

    /** Attiva o disattiva la modalità di modifica della dashboard. */
    fun toggleEditMode() {
        _state.update { it.copy(isEditMode = !it.isEditMode) }
    }

    /**
     * Salva il layout aggiornato (ricalcola le posizioni) e lo sincronizza sul backend.
     *
     * @param widgets Lista dei widget nell'ordine desiderato.
     */
    fun saveLayout(widgets: List<WidgetConfig>) {
        viewModelScope.launch {
            val reordered = widgets.mapIndexed { i, w -> w.copy(position = i) }
            _state.update { it.copy(widgets = reordered, isEditMode = false) }
            repo.saveLayout(utente, reordered)
            repo.syncToRemote(utente, reordered)
        }
    }

    /**
     * Rimuove un widget dal layout per ID.
     *
     * @param id ID del widget da rimuovere.
     */
    fun removeWidget(id: String) {
        val updated = _state.value.widgets.filter { it.id != id }
        saveLayout(updated)
    }

    /**
     * Aggiunge un widget in fondo al layout corrente.
     *
     * @param widget Configurazione del nuovo widget.
     */
    fun addWidget(widget: WidgetConfig) {
        val updated = _state.value.widgets + widget
        saveLayout(updated)
    }

    /**
     * Sposta un widget di una posizione verso l'alto nella lista.
     *
     * @param id ID del widget da spostare.
     */
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

    /**
     * Sposta un widget di una posizione verso il basso nella lista.
     *
     * @param id ID del widget da spostare.
     */
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

    /**
     * Alterna la dimensione del widget tra [WidgetSize.WIDE] e [WidgetSize.SMALL].
     *
     * @param id ID del widget di cui cambiare la dimensione.
     */
    fun toggleSize(id: String) {
        val updated = _state.value.widgets.map { w ->
            if (w.id == id) w.copy(
                size = if (w.size == WidgetSize.WIDE) WidgetSize.SMALL else WidgetSize.WIDE
            ) else w
        }
        saveLayout(updated)
    }
}