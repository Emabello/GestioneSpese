/**
 * SpeseViewModel.kt
 *
 * ViewModel principale dell'app. Gestisce tutto lo stato UI legato alle spese:
 * - Lista movimenti ([SpeseUiState.spese]) con filtri ([SpeseFilters])
 * - Tabelle di lookup (tipi, categorie, conti, sottocategorie, UTC)
 * - Stato di sincronizzazione ([SpeseUiState.syncDone])
 * - Pre-compilazione del form da bozze bancarie (draft prefill)
 *
 * Usa lo pattern **offline-first**: i dati vengono letti da Room e
 * sincronizzati con il backend tramite [SpeseRepository] su richiesta esplicita.
 *
 * I tipi di dati definiti in questo file:
 * - [SpeseViewModel]: il ViewModel
 * - [SpeseUiState]: stato immutabile osservato dalla UI
 * - [SpeseFilters]: filtri applicati alla lista spese
 * - [FilterKey]: enum per identificare quale filtro resettare
 */
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

/**
 * ViewModel principale per la gestione delle spese dell'utente.
 *
 * @param repo          Repository per le operazioni su spese e lookup.
 * @param currentUtente ID dell'utente correntemente autenticato.
 */
class SpeseViewModel(private val repo: SpeseRepository, private val currentUtente: String) : ViewModel() {

    private val _state = MutableStateFlow(SpeseUiState())

    /** Stato UI osservabile dalla schermata principale e dal form. */
    val state: StateFlow<SpeseUiState> = _state

    /** Resetta il messaggio di errore corrente. */
    fun clearError() = _state.update { it.copy(error = null) }

    /** Consuma il tick di salvataggio riuscito (evita toast doppi). */
    fun consumeSaveOk() = _state.update { it.copy(saveOkTick = 0L) }

    /**
     * Pre-compila lo stato con i dati di una bozza bancaria.
     * Il form [SpesaFormScreen] leggerà questi valori tramite [SpeseUiState.draftPrefillTick].
     *
     * @param importo     Importo in euro dalla notifica.
     * @param descrizione Merchant/descrizione dalla notifica.
     * @param dataMillis  Timestamp Unix in ms della transazione.
     * @param metodo      Metodo di pagamento (es. `"Webank"`).
     */
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

    /** Cancella i dati di pre-compilazione dopo che il form li ha consumati. */
    fun clearDraftPrefill() = _state.update {
        it.copy(
            draftImporto = null,
            draftDescrizione = null,
            draftData = null,
            draftMetodo = null,
            draftPrefillTick = 0L
        )
    }

    // ── Filtri ────────────────────────────────────────────────────────────────

    /** Imposta il testo di ricerca libera. */
    fun setQuery(q: String) = _state.update { it.copy(filters = it.filters.copy(query = q)) }
    /** Filtra per mese (formato `"MM"` o `"YYYY-MM"`). */
    fun setMese(m: String?) = _state.update { it.copy(filters = it.filters.copy(mese = m)) }
    /** Filtra per tipo di movimento. */
    fun setTipo(t: String?) = _state.update { it.copy(filters = it.filters.copy(tipo = t)) }
    /** Filtra per metodo/conto. */
    fun setMetodo(m: String?) = _state.update { it.copy(filters = it.filters.copy(metodo = m)) }

    /**
     * Azzera un singolo filtro per chiave.
     *
     * @param key Il filtro da resettare ([FilterKey]).
     */
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

    /** Azzera tutti i filtri contemporaneamente. */
    fun resetFilters() = _state.update { it.copy(filters = SpeseFilters()) }

    // ── Spese ─────────────────────────────────────────────────────────────────

    /** Ricarica la lista spese dal DB locale. */
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

    /**
     * Sincronizza le spese dal backend e aggiorna la lista.
     * Usato per il pull-to-refresh esplicito dell'utente.
     * A differenza di [refresh], esegue anche la sync remota con [SpeseRepository.syncSpese].
     */
    fun pullRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                repo.syncSpese(currentUtente)
                repo.list(currentUtente)
            }.onSuccess { list ->
                _state.update { it.copy(loading = false, spese = list, didLoadSpese = true) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /**
     * Ricarica le spese solo se non sono già state caricate o se si forza.
     *
     * @param force `true` per forzare il ricaricamento anche se già caricato.
     */
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
        contoDestinazione: String? = null,
        descrizione: String?,
        categoria: String?,
        sottocategoria: String?
    ) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                val isNew = editingId == null || editingId == -1
                if (isNew) {
                    repo.add(
                        utente            = currentUtente,
                        data              = data,
                        conto             = conto,
                        contoDestinazione = contoDestinazione,
                        importo           = importo,
                        tipo              = tipo,
                        categoria         = categoria,
                        sottocategoria    = sottocategoria,
                        descrizione       = descrizione
                    )
                } else {
                    repo.update(
                        id                = editingId,
                        utente            = currentUtente,
                        data              = data,
                        conto             = conto,
                        contoDestinazione = contoDestinazione,
                        importo           = importo,
                        tipo              = tipo,
                        categoria         = categoria,
                        sottocategoria    = sottocategoria,
                        descrizione       = descrizione
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

    /**
     * Carica le lookup dal DB locale solo se non ancora caricate o se forzato.
     * Se Room è vuoto, scarica dal backend con [SpeseRepository.refreshLookupsFromRemoteAndSave].
     *
     * @param force `true` per forzare il ricaricamento.
     */
    fun loadLookupsIfNeeded(force: Boolean = false) {
        val st = _state.value
        if (force || (!st.didLoadLookups && !st.loadingLookups)) loadLookups()
    }

    /** Carica le lookup (tipi, categorie, conti, sottocategorie, UTC) dal DB locale. */
    fun loadLookups() = viewModelScope.launch {
        _state.update { it.copy(loadingLookups = true, error = null) }
        try {
            val local = repo.getLookupsFromDb(utenteId = currentUtente)
            val utcsLocal  = repo.getUtcsFromDb(utenteId = currentUtente)

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
            val utcsLocal2 = repo.getUtcsFromDb(utenteId = currentUtente)

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

    /**
     * Elimina una spesa per ID (sia dal backend che da Room).
     * Setta [SpeseUiState.deletingId] durante l'operazione per mostrare un
     * indicatore di caricamento sulla card corrispondente.
     *
     * @param id ID del movimento da eliminare.
     */
    fun delete(id: Int) {
        viewModelScope.launch {
            _state.update { it.copy(deletingId = id) }
            runCatching { repo.delete(id = id, utente = currentUtente) }
                .onSuccess {
                    _state.update { it.copy(spese = it.spese.filter { s -> s.id != id }, deletingId = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, deletingId = null) }
                }
        }
    }

    /**
     * Sincronizzazione completa: scarica spese e lookup dal backend e aggiorna Room.
     *
     * Se Room ha già dati (utente di ritorno), mostra subito la UI con i dati cached
     * e aggiorna in background senza bloccare l'utente. Solo al primo login (Room vuoto)
     * mostra la [SyncLoadingScreen] bloccante.
     *
     * Imposta [SpeseUiState.syncDone] a `true` non appena i dati sono disponibili
     * (immediatamente se da cache, oppure dopo la sync per il primo login).
     */
    fun syncAll() {
        viewModelScope.launch {
            // ── Step 1: leggi Room immediatamente (zero network) ──────────
            val localSpese   = repo.list(currentUtente)
            val localLookups = repo.getLookupsFromDb(utenteId = currentUtente)
            val localUtcs    = repo.getUtcsFromDb(utenteId = currentUtente)

            val hasLocal = localSpese.isNotEmpty()
                    && localLookups.tipi.isNotEmpty()
                    && localLookups.categorie.isNotEmpty()
                    && localLookups.conti.isNotEmpty()
                    && localUtcs.isNotEmpty()

            if (hasLocal) {
                // ── Step 2: mostra UI subito con dati cached ──────────────
                _state.update {
                    it.copy(
                        loading = false, loadingLookups = false,
                        spese = localSpese, didLoadSpese = true,
                        tipi = localLookups.tipi, categorie = localLookups.categorie,
                        conti = localLookups.conti, sottocategorie = localLookups.sottocategorie,
                        utcs = localUtcs, didLoadLookups = true,
                        syncDone = true, error = null
                    )
                }
                // ── Step 3: sync in background (nessuna loading screen) ───
                runCatching { repo.syncAllBatch(currentUtente) }
                    .onSuccess {
                        val fresh = repo.list(currentUtente)
                        val fl    = repo.getLookupsFromDb(utenteId = currentUtente)
                        val fu    = repo.getUtcsFromDb(utenteId = currentUtente)
                        _state.update {
                            it.copy(
                                spese = fresh, tipi = fl.tipi, categorie = fl.categorie,
                                conti = fl.conti, sottocategorie = fl.sottocategorie,
                                utcs = fu, didLoadSpese = true, didLoadLookups = true, error = null
                            )
                        }
                    }
                    .onFailure { /* silenzioso: l'utente ha già dati validi da cache */ }
            } else {
                // ── Step 4: primo login (Room vuoto) — sync bloccante ─────
                _state.update { it.copy(loading = true, loadingLookups = true, error = null, syncDone = false) }
                runCatching { repo.syncAllBatch(currentUtente) }
                    .onSuccess {
                        val fresh = repo.list(currentUtente)
                        val fl    = repo.getLookupsFromDb(utenteId = currentUtente)
                        val fu    = repo.getUtcsFromDb(utenteId = currentUtente)
                        _state.update {
                            it.copy(
                                loading = false, loadingLookups = false,
                                spese = fresh, didLoadSpese = true,
                                tipi = fl.tipi, categorie = fl.categorie, conti = fl.conti,
                                sottocategorie = fl.sottocategorie, utcs = fu,
                                didLoadLookups = true, syncDone = true
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
}



/**
 * Filtri attivi per la lista spese nella [HomeScreen].
 *
 * @property query  Testo di ricerca libera.
 * @property mese   Mese selezionato come filtro temporale, o `null`.
 * @property tipo   Tipo di movimento selezionato, o `null`.
 * @property metodo Conto/metodo di pagamento selezionato, o `null`.
 */
data class SpeseFilters(
    val query: String = "",
    val mese: String? = null,
    val tipo: String? = null,
    val metodo: String? = null
)

/** Chiave per identificare quale filtro resettare con [SpeseViewModel.clearFilter]. */
enum class FilterKey { MESE, TIPO, METODO, QUERY }

/**
 * Stato UI immutabile osservato da [HomeScreen], [SpesaFormScreen] e [SummaryScreen].
 *
 * @property syncDone        `true` dopo il completamento della sincronizzazione iniziale.
 * @property didLoadSpese    `true` se le spese sono state caricate almeno una volta.
 * @property didLoadLookups  `true` se le lookup sono state caricate almeno una volta.
 * @property loading         `true` durante il caricamento delle spese.
 * @property loadingLookups  `true` durante il caricamento delle lookup.
 * @property saving          `true` durante il salvataggio di una spesa.
 * @property deletingId      ID della spesa in fase di eliminazione, o `null` se nessuna.
 * @property saveOkTick      Timestamp del salvataggio riuscito (usato per il toast).
 * @property draftPrefillTick Timestamp dell'ultimo prefill da bozza bancaria.
 * @property error           Messaggio di errore corrente, o `null`.
 * @property spese           Lista completa dei movimenti dell'utente.
 * @property utcs            Associazioni UTC per il suggerimento automatico della categoria.
 * @property filters         Filtri attivi sulla lista spese.
 * @property tipi            Lista dei tipi di movimento disponibili.
 * @property categorie       Lista delle categorie disponibili.
 * @property sottocategorie  Lista delle sottocategorie disponibili.
 * @property conti           Lista dei conti disponibili.
 * @property draftImporto    Importo pre-compilato da bozza bancaria.
 * @property draftDescrizione Descrizione pre-compilata da bozza bancaria.
 * @property draftData       Data pre-compilata da bozza bancaria (formato `"yyyy-MM-dd"`).
 * @property draftMetodo     Metodo di pagamento pre-compilato da bozza bancaria.
 */
data class SpeseUiState(
    val syncDone: Boolean = false,
    val didLoadSpese: Boolean = false,
    val didLoadLookups: Boolean = false,
    val loading: Boolean = false,
    val loadingLookups: Boolean = false,
    val saving: Boolean = false,
    val deletingId: Int? = null,
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
