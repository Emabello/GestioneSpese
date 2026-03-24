/**
 * ConfigScreen.kt
 *
 * Schermata di configurazione con due tab (UTCS / Conti):
 * - UTCS: drill-down 3 livelli (Tipologia → Categoria → Sottocategoria)
 *         Il filtro usa allUtcs come sorgente della gerarchia.
 * - Conti: lista flat con inserimento automatico UC.
 *
 * Creazione automatica associazioni:
 * - Nuova Sottocategoria → inserisce SOTTOCATEGORIA + UTCS
 * - Nuovo Conto          → inserisce CONTO + UC
 */
package com.emanuele.gestionespese.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.model.*
import com.emanuele.gestionespese.data.repo.stripFormulaFields
import com.emanuele.gestionespese.ui.theme.Brand
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// ── Tab principali ────────────────────────────────────────────────────
private enum class ConfigTab { UTCS, CONTI }

// ── Livello drill-down UTCS ───────────────────────────────────────────
private enum class DrillLevel { TIPOLOGIA, CATEGORIA, SOTTOCATEGORIA }

// ── ConfigTable ───────────────────────────────────────────────────────
enum class ConfigTable(val label: String, val resource: String) {
    TIPOLOGIA     ("Tipologie",           "tipologia"),
    CATEGORIA     ("Categorie",           "categoria"),
    SOTTOCATEGORIA("Sottocategorie",      "sottocategoria"),
    CONTO         ("Conti",               "conto"),
    UC            ("Conti utente (UC)",   "uc"),
    UTCS          ("Combinazioni (UTCS)", "utcs")
}

// ── Campi da non toccare mai in update/insert ─────────────────────────
private val READONLY_FIELDS = setOf(
    "id_definizione", "id_definizione_attivo",
    "id", "lista_conto_riga", "mese", "anno"
)

private fun Map<String, Any?>.safePayload(): Map<String, Any?> =
    filterKeys { it.lowercase() !in READONLY_FIELDS }

private fun Map<String, Any?>.sanitizeForSheet(): Map<String, Any?> =
    safePayload().stripFormulaFields()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(onBack: () -> Unit) {
    val context       = LocalContext.current
    val app           = context.applicationContext as MyApp
    val api           = app.api
    val scope         = rememberCoroutineScope()
    val currentUtente = app.currentUserLabel ?: ""

    // ── Navigazione ───────────────────────────────────────────────────
    var activeTab    by remember { mutableStateOf(ConfigTab.UTCS) }
    var drillLevel   by remember { mutableStateOf(DrillLevel.TIPOLOGIA) }
    var selectedTipo by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var selectedCat  by remember { mutableStateOf<Map<String, Any?>?>(null) }

    // ── Stato UI ──────────────────────────────────────────────────────
    var isLoading       by remember { mutableStateOf(false) }
    var isRefreshing    by remember { mutableStateOf(false) }
    // id record con spinner locale
    var loadingRecordId by remember { mutableStateOf<String?>(null) }
    var isMutating      by remember { mutableStateOf(false) }
    var errorMsg        by remember { mutableStateOf<String?>(null) }

    // ── Dialog stato ──────────────────────────────────────────────────
    var showAddDialog     by remember { mutableStateOf(false) }
    var showEditDialog    by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedRecord    by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var dialogTable       by remember { mutableStateOf(ConfigTable.TIPOLOGIA) }

    // ── Cache dati ────────────────────────────────────────────────────
    var allCategorie      by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allConti          by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allTipologie      by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allSottocategorie by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allUc             by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allUtcs           by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    // extractId e recordId sono definite a livello file (extractIdStatic)
    // usiamo un alias locale per leggibilità
    fun extractId(v: Any?): String = extractIdStatic(v)
    fun recordId(r: Map<String, Any?>): String = extractIdStatic(r["id"])

    fun findUtcsRowsForRecord(record: Map<String, Any?>, table: ConfigTable): List<Map<String, Any?>> {
        val recordKey = extractId(record["id"])
        if (recordKey.isBlank()) return emptyList()
        return when (table) {
            ConfigTable.TIPOLOGIA -> {
                allUtcs.filter {
                    extractId(it["id_tipologia"]) == recordKey &&
                            java.lang.String(it["id_utente"]?.toString() ?: "").trim() == currentUtente
                }
            }
            ConfigTable.CATEGORIA -> {
                val selectedTipoId = extractId(selectedTipo?.get("id"))
                allUtcs.filter {
                    extractId(it["id_categoria"]) == recordKey &&
                            java.lang.String(it["id_utente"]?.toString() ?: "").trim() == currentUtente &&
                            (selectedTipoId.isBlank() || extractId(it["id_tipologia"]) == selectedTipoId)
                }
            }
            ConfigTable.SOTTOCATEGORIA -> {
                val selectedTipoId = extractId(selectedTipo?.get("id"))
                val selectedCatId = extractId(selectedCat?.get("id"))
                allUtcs.filter {
                    extractId(it["id_sottocategoria"]) == recordKey &&
                            java.lang.String(it["id_utente"]?.toString() ?: "").trim() == currentUtente &&
                            (selectedTipoId.isBlank() || extractId(it["id_tipologia"]) == selectedTipoId) &&
                            (selectedCatId.isBlank() || extractId(it["id_categoria"]) == selectedCatId)
                }
            }
            else -> emptyList()
        }
    }

    fun findUcRowsForRecord(record: Map<String, Any?>): List<Map<String, Any?>> {
        val recordKey = extractId(record["id"])
        if (recordKey.isBlank()) return emptyList()
        return allUc.filter {
            extractId(it["id_conto"]) == recordKey &&
                    java.lang.String(it["id_utente"]?.toString() ?: "").trim() == currentUtente
        }
    }

    fun attachRelationActive(record: Map<String, Any?>, table: ConfigTable): Map<String, Any?> {
        val relationRows = when (table) {
            ConfigTable.TIPOLOGIA,
            ConfigTable.CATEGORIA,
            ConfigTable.SOTTOCATEGORIA -> findUtcsRowsForRecord(record, table)
            ConfigTable.CONTO -> findUcRowsForRecord(record)
            else -> emptyList()
        }
        if (relationRows.isEmpty()) return record

        val relationActive = relationRows.any { row ->
            row["attivo"] == true || row["attivo"]?.toString()?.lowercase() == "true"
        }
        val attivoKey = if (table == ConfigTable.CATEGORIA) "attiva" else "attivo"
        return record + mapOf(attivoKey to relationActive)
    }

    // ── Filtro drill-down via UTCS ────────────────────────────────────
    // derivedStateOf garantisce ricalcolo reattivo quando allUtcs/drillLevel cambiano
    val utcsFiltrati by remember {
        derivedStateOf {
            allUtcs.filter {
                java.lang.String(it["id_utente"]?.toString() ?: "").trim() == currentUtente
            }
        }
    }

    val currentList by remember {
        derivedStateOf {
            when {
                activeTab == ConfigTab.CONTI ->
                    allConti.map { attachRelationActive(it, ConfigTable.CONTO) }

                drillLevel == DrillLevel.TIPOLOGIA -> {
                    val tipoIds = utcsFiltrati
                        .map { extractId(it["id_tipologia"]) }
                        .filter { it.isNotBlank() }
                        .toSet()
                    allTipologie
                        .filter { extractId(it["id"]) in tipoIds }
                        .map { attachRelationActive(it, ConfigTable.TIPOLOGIA) }
                }

                drillLevel == DrillLevel.CATEGORIA -> {
                    val tipoId = extractId(selectedTipo?.get("id"))
                    val catIds = utcsFiltrati
                        .filter { extractId(it["id_tipologia"]) == tipoId }
                        .map    { extractId(it["id_categoria"]) }
                        .filter { it.isNotBlank() }
                        .toSet()
                    allCategorie
                        .filter { extractId(it["id"]) in catIds }
                        .map { attachRelationActive(it, ConfigTable.CATEGORIA) }
                }

                else -> {
                    val tipoId = extractId(selectedTipo?.get("id"))
                    val catId  = extractId(selectedCat?.get("id"))
                    val sottoIds = utcsFiltrati
                        .filter {
                            extractId(it["id_tipologia"]) == tipoId &&
                                    extractId(it["id_categoria"])  == catId
                        }
                        .map    { extractId(it["id_sottocategoria"]) }
                        .filter { it.isNotBlank() }
                        .toSet()
                    allSottocategorie
                        .filter { extractId(it["id"]) in sottoIds }
                        .map { attachRelationActive(it, ConfigTable.SOTTOCATEGORIA) }
                }
            }
        }
    }

    // ── ConfigTable corrente ──────────────────────────────────────────
    val currentTable: ConfigTable = when {
        activeTab == ConfigTab.CONTI          -> ConfigTable.CONTO
        drillLevel == DrillLevel.TIPOLOGIA    -> ConfigTable.TIPOLOGIA
        drillLevel == DrillLevel.CATEGORIA    -> ConfigTable.CATEGORIA
        else                                  -> ConfigTable.SOTTOCATEGORIA
    }

    // ── Breadcrumb ────────────────────────────────────────────────────
    val breadcrumb: String = when {
        activeTab == ConfigTab.CONTI -> "Conti"
        drillLevel == DrillLevel.TIPOLOGIA -> "Tipologie"
        drillLevel == DrillLevel.CATEGORIA ->
            "Tipologie  ›  ${selectedTipo?.get("descrizione") ?: "—"}"
        else ->
            "Tipologie  ›  ${selectedTipo?.get("descrizione") ?: "—"}  ›  ${selectedCat?.get("descrizione") ?: "—"}"
    }

    // ── Back handler ──────────────────────────────────────────────────
    val canGoBack = activeTab == ConfigTab.UTCS && drillLevel != DrillLevel.TIPOLOGIA
    BackHandler(enabled = canGoBack) {
        when (drillLevel) {
            DrillLevel.SOTTOCATEGORIA -> { drillLevel = DrillLevel.CATEGORIA;  selectedCat  = null }
            DrillLevel.CATEGORIA      -> { drillLevel = DrillLevel.TIPOLOGIA;  selectedTipo = null }
            else -> {}
        }
    }
    BackHandler(enabled = isMutating) { /* blocca back durante mutazioni */ }

    // ── Snackbar ──────────────────────────────────────────────────────
    var snackMsg          by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snackbarHostState.showSnackbar(it); snackMsg = null }
    }

    errorMsg?.let { err ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            title = { Text("Operazione non riuscita") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { errorMsg = null }) { Text("OK") }
            }
        )
    }

    // ── Carica dati ───────────────────────────────────────────────────
    // Tipi, categorie, sottocategorie: da Room (già sincronizzate da syncAllBatch,
    // zero chiamate API). UTCs dall'API perché il loro id numerico non è in Room
    // ed è necessario per le operazioni CRUD (cascade deactivate, ecc.).
    // Conti e UC vengono caricati lazy nel LaunchedEffect(activeTab) → CONTI.
    fun loadAllData(forceRefresh: Boolean = false) {
        scope.launch {
            errorMsg = null
            try {
                val db = (context.applicationContext as MyApp).db

                val localTipologie = db.lookupDao().getTipiRaw().map { e ->
                    val p = e.value.split(" - ", limit = 2)
                    mapOf<String, Any?>(
                        "id"             to p.getOrNull(0),
                        "descrizione"    to p.getOrNull(1),
                        "attivo"         to e.attivo,
                        "tipo_movimento" to e.tipoMovimento
                    )
                }
                val localCategorie = db.lookupDao().getCategorieRaw().map { e ->
                    val p = e.value.split(" - ", limit = 2)
                    mapOf<String, Any?>(
                        "id"          to p.getOrNull(0),
                        "descrizione" to p.getOrNull(1),
                        "attiva"      to e.attivo
                    )
                }
                val localSottocategorie = db.lookupDao().getSottocategorieRaw().map { e ->
                    mapOf<String, Any?>(
                        "id"           to e.id,
                        "id_categoria" to e.categoria,
                        "descrizione"  to e.sottocategoria,
                        "attivo"       to e.attivo
                    )
                }
                val localUtcs = db.lookupDao().getUtcsByUtente(currentUtente).map { e ->
                    mapOf<String, Any?>(
                        "id" to e.key,
                        "id_utente" to e.utente,
                        "id_tipologia" to e.tipologia,
                        "id_categoria" to e.categoria,
                        "id_sottocategoria" to e.sottocategoria,
                        "attivo" to e.attivo
                    )
                }
                val localConti = db.lookupDao().getContiRaw(currentUtente).map { e ->
                    val p = e.value.split(" - ", limit = 2)
                    mapOf<String, Any?>(
                        "id" to p.getOrNull(0),
                        "descrizione" to p.getOrNull(1),
                        "attivo" to e.attivo
                    )
                }

                val hasLocal = localTipologie.isNotEmpty() || localCategorie.isNotEmpty() || localSottocategorie.isNotEmpty() || localUtcs.isNotEmpty() || localConti.isNotEmpty()
                isLoading = !hasLocal

                if (forceRefresh || allTipologie.isEmpty()) allTipologie = localTipologie
                if (forceRefresh || allCategorie.isEmpty()) allCategorie = localCategorie
                if (forceRefresh || allSottocategorie.isEmpty()) allSottocategorie = localSottocategorie
                if (forceRefresh || allUtcs.isEmpty()) allUtcs = localUtcs
                if (forceRefresh || allConti.isEmpty()) allConti = localConti

                // Refresh remoto silenzioso: serve per avere gli id numerici reali utili ai CRUD.
                allUtcs = api.getUtcs().data ?: allUtcs
                allConti = api.getConti().data ?: allConti
                allUc = api.getUc(utente = currentUtente).data ?: allUc
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // ── Refresh mirato con filtri drill-down ──────────────────────────
    suspend fun refreshCurrentLevel(showTopLoading: Boolean = true) {
        if (showTopLoading) isRefreshing = true
        errorMsg = null
        try {
            coroutineScope {
                // Ricarica sempre UTCS (fonte gerarchia) + la tabella corrente
                val utcsD = async { api.getUtcs().data ?: emptyList() }
                when {
                    activeTab == ConfigTab.CONTI -> {
                        allConti = api.getConti().data ?: emptyList()
                        allUc    = api.getUc(utente = currentUtente).data ?: emptyList()
                    }
                    drillLevel == DrillLevel.TIPOLOGIA ->
                        allTipologie = api.getTipi().data ?: emptyList()
                    drillLevel == DrillLevel.CATEGORIA ->
                        allCategorie = api.getCategorie().data
                            ?.map { mapOf("id" to it.id, "descrizione" to it.descrizione, "attiva" to it.attiva) }
                            ?: emptyList()
                    else ->
                        allSottocategorie = api.getSottocategorie().data ?: emptyList()
                }
                allUtcs = utcsD.await()
            }
        } catch (e: Exception) {
            errorMsg = e.message
        } finally {
            if (showTopLoading) isRefreshing = false
        }
    }

    // ── Cascade deactivate ────────────────────────────────────────────
    suspend fun cascadeDeactivate(table: ConfigTable, recordId: Int) {
        try {
            val idStr = recordId.toString()
            val toDeactivateUtcs = when (table) {
                ConfigTable.TIPOLOGIA      -> allUtcs.filter { extractId(it["id_tipologia"])      == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                ConfigTable.CATEGORIA      -> allUtcs.filter { extractId(it["id_categoria"])      == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                ConfigTable.SOTTOCATEGORIA -> allUtcs.filter { extractId(it["id_sottocategoria"]) == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                else -> emptyList()
            }
            val toDeactivateSottocat = when (table) {
                ConfigTable.CATEGORIA -> allSottocategorie.filter { extractId(it["id_categoria"]) == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                else -> emptyList()
            }
            val toDeactivateUc = when (table) {
                ConfigTable.CONTO -> allUc.filter { extractId(it["id_conto"]) == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                else -> emptyList()
            }
            val totalCount = toDeactivateUtcs.size + toDeactivateSottocat.size + toDeactivateUc.size
            if (totalCount == 0) return

            coroutineScope {
                val jobs = mutableListOf<kotlinx.coroutines.Deferred<*>>()
                toDeactivateUtcs.forEach { r ->
                    val rid = (r["id"] as? Double)?.toInt() ?: r["id"].toString().toIntOrNull() ?: return@forEach
                    jobs += async { api.updateRecord(GenericUpdateRequest(resource = ConfigTable.UTCS.resource, id = rid, data = mapOf("attivo" to false).sanitizeForSheet())) }
                }
                toDeactivateSottocat.forEach { r ->
                    val rid = (r["id"] as? Double)?.toInt() ?: r["id"].toString().toIntOrNull() ?: return@forEach
                    jobs += async { api.updateRecord(GenericUpdateRequest(resource = ConfigTable.SOTTOCATEGORIA.resource, id = rid, data = mapOf("attivo" to false).sanitizeForSheet())) }
                }
                toDeactivateUc.forEach { r ->
                    val rid = (r["id"] as? Double)?.toInt() ?: r["id"].toString().toIntOrNull() ?: return@forEach
                    jobs += async { api.updateRecord(GenericUpdateRequest(resource = ConfigTable.UC.resource, id = rid, data = mapOf("attivo" to false).sanitizeForSheet())) }
                }
                jobs.awaitAll()
            }
            snackMsg = "$totalCount ${if (totalCount == 1) "voce correlata disattivata" else "voci correlate disattivate"}"
            refreshCurrentLevel(showTopLoading = false)
        } catch (e: Exception) {
            errorMsg = e.message
        }
    }

    // Carica all'avvio: tipi/categorie/sotto da Room, UTCs dall'API
    LaunchedEffect(Unit) { loadAllData() }

    fun addOptimisticRecord(table: ConfigTable, newId: String, data: Map<String, Any?>) {
        val optimisticRecord = when (table) {
            ConfigTable.TIPOLOGIA -> mapOf(
                "id" to newId,
                "descrizione" to (data["descrizione"]?.toString() ?: ""),
                "tipo_movimento" to (data["tipo_movimento"]?.toString() ?: "uscita"),
                "attivo" to true
            )
            ConfigTable.CATEGORIA -> mapOf(
                "id" to newId,
                "descrizione" to (data["descrizione"]?.toString() ?: ""),
                "attiva" to true
            )
            ConfigTable.SOTTOCATEGORIA -> mapOf(
                "id" to newId,
                "id_categoria" to (data["id_categoria"]?.toString() ?: selectedCat?.get("id")?.toString().orEmpty()),
                "descrizione" to (data["descrizione"]?.toString() ?: ""),
                "attivo" to true
            )
            ConfigTable.CONTO -> mapOf(
                "id" to newId,
                "descrizione" to (data["descrizione"]?.toString() ?: ""),
                "attivo" to true
            )
            else -> emptyMap()
        }
        when (table) {
            ConfigTable.TIPOLOGIA -> allTipologie = (allTipologie + optimisticRecord).distinctBy { recordId(it) }
            ConfigTable.CATEGORIA -> allCategorie = (allCategorie + optimisticRecord).distinctBy { recordId(it) }
            ConfigTable.SOTTOCATEGORIA -> allSottocategorie = (allSottocategorie + optimisticRecord).distinctBy { recordId(it) }
            ConfigTable.CONTO -> allConti = (allConti + optimisticRecord).distinctBy { recordId(it) }
            else -> Unit
        }
    }

    suspend fun resolveInsertedTipologiaId(descrizione: String, tipoMovimento: String): String? {
        val remote = api.getTipi().data ?: return null
        val match = remote
            .filter {
                (it["descrizione"]?.toString()?.trim().orEmpty() == descrizione.trim()) &&
                        (it["tipo_movimento"]?.toString()?.trim().orEmpty() == tipoMovimento.trim())
            }
            .maxByOrNull { extractId(it["id"]).toIntOrNull() ?: Int.MIN_VALUE }
        return match?.let { extractId(it["id"]) }?.takeIf { it.isNotBlank() }
    }

    // ── Dialog Aggiungi / Modifica ────────────────────────────────────
    if (showAddDialog || showEditDialog) {
        ConfigRecordDialog(
            table             = dialogTable,
            record            = if (showEditDialog) selectedRecord else null,
            currentUtente     = currentUtente,
            allCategorie      = allCategorie,
            allConti          = allConti,
            allTipologie      = allTipologie,
            allSottocategorie = allSottocategorie,
            preselectedTipoId = selectedTipo?.get("id")?.toString(),
            preselectedCatId  = selectedCat?.get("id")?.toString(),
            onDismiss         = { showAddDialog = false; showEditDialog = false },
            onSave            = { data ->
                scope.launch {
                    val editingRecordId = selectedRecord?.let(::recordId)
                    val isEditing = showEditDialog && selectedRecord != null
                    var targetLoadingRecordId: String? = editingRecordId
                    isMutating = true
                    if (isEditing && !editingRecordId.isNullOrBlank()) {
                        loadingRecordId = editingRecordId
                    }
                    showAddDialog = false
                    showEditDialog = false
                    try {
                        if (isEditing && selectedRecord != null) {
                            // ── MODIFICA: solo campi sicuri ───────────
                            val id = (selectedRecord!!["id"] as? Double)?.toInt()
                                ?: selectedRecord!!["id"].toString().toIntOrNull() ?: return@launch
                            api.updateRecord(
                                GenericUpdateRequest(
                                    resource = dialogTable.resource,
                                    id       = id,
                                    data     = data.sanitizeForSheet()
                                )
                            )
                            snackMsg = "Record aggiornato in ${dialogTable.label}"
                        } else {
                            // ── INSERIMENTO ───────────────────────────
                            val insertResp = api.insertRecord(
                                GenericInsertRequest(
                                    resource = dialogTable.resource,
                                    data     = data.sanitizeForSheet()
                                )
                            )

                            // Recupera l'id del record appena creato
                            var newId = insertResp.data
                                ?.let { it["id"] ?: it["insertedId"] }
                                ?.let { v -> (v as? Double)?.toInt()?.toString() ?: v.toString() }

                            if (dialogTable == ConfigTable.TIPOLOGIA && newId.isNullOrBlank()) {
                                newId = resolveInsertedTipologiaId(
                                    descrizione = data["descrizione"]?.toString().orEmpty(),
                                    tipoMovimento = data["tipo_movimento"]?.toString().orEmpty()
                                )
                            }

                            // ── Auto-associazione in base al tipo di record creato ──
                            // Gli id in UTCS devono essere solo numerici (es. "1", "4")
                            // non nel formato completo "1 - Debiti".
                            // extractId() garantisce che prendiamo solo la parte numerica.

                            if (!newId.isNullOrBlank()) {
                                addOptimisticRecord(dialogTable, newId, data)
                                loadingRecordId = newId
                                targetLoadingRecordId = newId
                            }

                            when (dialogTable) {

                                ConfigTable.TIPOLOGIA -> {
                                    if (newId != null) {
                                        val tipoLabel = "${newId} - ${data["descrizione"]?.toString() ?: ""}"
                                        val newUtcsRow = api.insertRecord(
                                            GenericInsertRequest(
                                                resource = ConfigTable.UTCS.resource,
                                                data     = mapOf(
                                                    "id_utente"         to currentUtente,
                                                    "id_tipologia"      to tipoLabel,
                                                    "id_categoria"      to "",
                                                    "id_sottocategoria" to "",
                                                    "attivo"            to true
                                                ).sanitizeForSheet()
                                            )
                                        ).data ?: emptyMap()
                                        allUtcs = allUtcs + newUtcsRow
                                    }
                                }

                                // Nuova Categoria → UTCS placeholder (sottocategoria vuota)
                                // così la categoria è subito visibile nel drill-down.
                                // I campi FK in UTCS devono essere nel formato "id - descrizione"
                                // come tutti gli altri record esistenti nello sheet.
                                ConfigTable.CATEGORIA -> {
                                    // selectedTipo ha già il formato completo dal server (es. "2 - Reddito")
                                    // Per la categoria appena creata costruiamo "newId - descrizione"
                                    val tipoLabel = selectedTipo?.let {
                                        val tid = extractId(it["id"])
                                        val desc = it["descrizione"]?.toString() ?: ""
                                        "$tid - $desc"
                                    } ?: ""
                                    val catLabel = if (newId != null) {
                                        val desc = data["descrizione"]?.toString() ?: ""
                                        "$newId - $desc"
                                    } else ""
                                    if (tipoLabel.isNotBlank() && catLabel.isNotBlank()) {
                                        val newUtcsRow = api.insertRecord(
                                            GenericInsertRequest(
                                                resource = ConfigTable.UTCS.resource,
                                                data     = mapOf(
                                                    "id_utente"         to currentUtente,
                                                    "id_tipologia"      to tipoLabel,
                                                    "id_categoria"      to catLabel,
                                                    "id_sottocategoria" to "",
                                                    "attivo"            to true
                                                ).sanitizeForSheet()
                                            )
                                        ).data ?: emptyMap()
                                        allUtcs = allUtcs + newUtcsRow
                                    }
                                }

                                // Nuova Sottocategoria → cerca placeholder UTCS (sotto vuota)
                                // Se esiste → update, altrimenti → insert nuovo record
                                ConfigTable.SOTTOCATEGORIA -> {
                                    val tipoLabel = selectedTipo?.let {
                                        val tid = extractId(it["id"])
                                        val desc = it["descrizione"]?.toString() ?: ""
                                        "$tid - $desc"
                                    } ?: ""
                                    val catLabel = selectedCat?.let {
                                        val cid = extractId(it["id"])
                                        val desc = it["descrizione"]?.toString() ?: ""
                                        "$cid - $desc"
                                    } ?: ""
                                    val sottoLabel = if (newId != null) {
                                        val desc = data["descrizione"]?.toString() ?: ""
                                        "$newId - $desc"
                                    } else ""
                                    if (tipoLabel.isNotBlank() && catLabel.isNotBlank() && sottoLabel.isNotBlank()) {
                                        // Cerca placeholder: stessa tipo+cat, id_sottocategoria vuoto
                                        val placeholder = allUtcs.firstOrNull { r ->
                                            extractId(r["id_tipologia"]) == extractId(selectedTipo?.get("id")) &&
                                                    extractId(r["id_categoria"])  == extractId(selectedCat?.get("id"))  &&
                                                    (r["id_sottocategoria"]?.toString()?.isBlank() == true ||
                                                            r["id_sottocategoria"] == null)
                                        }
                                        val placeholderId = placeholder?.get("id")?.let { v ->
                                            (v as? Double)?.toInt() ?: v.toString().toDoubleOrNull()?.toInt()
                                        }
                                        if (placeholderId != null) {
                                            // Aggiorna il placeholder esistente
                                            api.updateRecord(
                                                GenericUpdateRequest(
                                                    resource = ConfigTable.UTCS.resource,
                                                    id       = placeholderId,
                                                    data     = mapOf(
                                                        "id_sottocategoria" to sottoLabel,
                                                        "attivo"            to true
                                                    ).sanitizeForSheet()
                                                )
                                            )
                                        } else {
                                            // Nessun placeholder → insert diretto
                                            val newUtcsRow = api.insertRecord(
                                                GenericInsertRequest(
                                                    resource = ConfigTable.UTCS.resource,
                                                    data     = mapOf(
                                                        "id_utente"         to currentUtente,
                                                        "id_tipologia"      to tipoLabel,
                                                        "id_categoria"      to catLabel,
                                                        "id_sottocategoria" to sottoLabel,
                                                        "attivo"            to true
                                                    ).sanitizeForSheet()
                                                )
                                            ).data ?: emptyMap()
                                            allUtcs = allUtcs + newUtcsRow
                                        }
                                    }
                                }

                                // Nuovo Conto → UC automatico
                                ConfigTable.CONTO -> {
                                    if (newId != null) {
                                        val newUcRow = api.insertRecord(
                                            GenericInsertRequest(
                                                resource = ConfigTable.UC.resource,
                                                data     = mapOf(
                                                    "id_utente" to currentUtente,
                                                    "id_conto"  to newId,
                                                    "attivo"    to true
                                                ).sanitizeForSheet()
                                            )
                                        ).data ?: emptyMap()
                                        allUc = allUc + newUcRow
                                    }
                                }

                                else -> { /* Tipologia e altri: nessuna associazione automatica */ }
                            }

                            snackMsg = "Aggiunto in ${dialogTable.label}"
                        }
                        if (isEditing && !editingRecordId.isNullOrBlank()) {
                            loadingRecordId = editingRecordId
                        }
                        refreshCurrentLevel(showTopLoading = false)
                    } catch (e: Exception) {
                        errorMsg = e.message
                    } finally {
                        if (loadingRecordId == targetLoadingRecordId) {
                            loadingRecordId = null
                        }
                        isMutating = false
                    }
                }
            }
        )
    }

    // ── Dialog conferma elimina ───────────────────────────────────────
    if (showDeleteConfirm && selectedRecord != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Elimina record") },
            text  = { Text("Confermi l'eliminazione? L'operazione è definitiva.") },
            confirmButton = {
                TextButton(onClick = {
                    val record = selectedRecord ?: return@TextButton
                    val thisId = recordId(record)
                    showDeleteConfirm = false
                    if (thisId.isNotBlank()) {
                        loadingRecordId = thisId
                        isMutating = true
                        scope.launch {
                            try {
                                when (dialogTable) {
                                    ConfigTable.TIPOLOGIA,
                                    ConfigTable.CATEGORIA,
                                    ConfigTable.SOTTOCATEGORIA -> {
                                        val rowsToDelete = findUtcsRowsForRecord(record, dialogTable)
                                        rowsToDelete.forEach { utcsRow ->
                                            val utcsId = (utcsRow["id"] as? Double)?.toInt()
                                                ?: utcsRow["id"].toString().toIntOrNull()
                                                ?: return@forEach
                                            api.deleteRecord(GenericDeleteRequest(resource = ConfigTable.UTCS.resource, id = utcsId))
                                        }
                                        snackMsg = if (rowsToDelete.isEmpty()) "Nessuna riga UTCS da eliminare" else "Relazione UTCS eliminata"
                                    }
                                    ConfigTable.CONTO -> {
                                        val contoId = (record["id"] as? Double)?.toInt()
                                            ?: record["id"]?.toString()?.toIntOrNull()
                                        if (contoId != null) {
                                            api.deleteRecord(
                                                GenericDeleteRequest(
                                                    resource = ConfigTable.CONTO.resource,
                                                    id = contoId
                                                )
                                            )
                                        }
                                        val rowsToDelete = findUcRowsForRecord(record)
                                        rowsToDelete.forEach { ucRow ->
                                            val ucId = (ucRow["id"] as? Double)?.toInt()
                                                ?: ucRow["id"].toString().toIntOrNull()
                                                ?: return@forEach
                                            api.deleteRecord(GenericDeleteRequest(resource = ConfigTable.UC.resource, id = ucId))
                                        }
                                        snackMsg = "Conto eliminato${if (rowsToDelete.isNotEmpty()) " e link UC puliti" else ""}"
                                    }
                                    else -> Unit
                                }
                                refreshCurrentLevel(showTopLoading = false)
                            } catch (e: Exception) {
                                errorMsg = e.message
                            } finally { loadingRecordId = null; isMutating = false }
                        }
                    }
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") } }
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestione dati")
                        Text(breadcrumb,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isMutating) return@IconButton
                        if (canGoBack) {
                            when (drillLevel) {
                                DrillLevel.SOTTOCATEGORIA -> { drillLevel = DrillLevel.CATEGORIA;  selectedCat  = null }
                                DrillLevel.CATEGORIA      -> { drillLevel = DrillLevel.TIPOLOGIA;  selectedTipo = null }
                                else -> {}
                            }
                        } else onBack()
                    }, enabled = !isMutating) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isMutating) scope.launch { refreshCurrentLevel() } },
                        enabled = !isRefreshing && !isLoading && !isMutating
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color       = Brand
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Aggiorna", tint = Brand)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = {
                    if (isMutating) return@FloatingActionButton
                    dialogTable   = currentTable
                    showAddDialog = true
                },
                containerColor = Brand,
                modifier = Modifier
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi", tint = Color.White)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = activeTab == ConfigTab.UTCS,
                    onClick  = {
                        if (isMutating) return@NavigationBarItem
                        activeTab    = ConfigTab.UTCS
                        drillLevel   = DrillLevel.TIPOLOGIA
                        selectedTipo = null; selectedCat = null
                    },
                    enabled = !isMutating,
                    icon  = {},
                    label = { Text("UTCS") }
                )
                NavigationBarItem(
                    selected = activeTab == ConfigTab.CONTI,
                    onClick  = { if (!isMutating) activeTab = ConfigTab.CONTI },
                    enabled = !isMutating,
                    icon  = {},
                    label = { Text("Conti") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            errorMsg?.let { err ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text("Errore: $err", modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Brand)

            Text("${currentList.size} elementi",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding      = PaddingValues(bottom = 120.dp)
            ) {
                items(currentList, key = { recordId(it).ifBlank { it.hashCode().toString() } }) { record ->
                    val isDrillable = activeTab == ConfigTab.UTCS &&
                            drillLevel != DrillLevel.SOTTOCATEGORIA
                    val thisId = recordId(record)

                    ConfigRecordCard(
                        table          = currentTable,
                        record         = record,
                        isDrillable    = isDrillable,
                        isLoading      = loadingRecordId == thisId && thisId.isNotBlank(),
                        onDrillDown    = {
                            when (drillLevel) {
                                DrillLevel.TIPOLOGIA -> { selectedTipo = record; drillLevel = DrillLevel.CATEGORIA }
                                DrillLevel.CATEGORIA -> { selectedCat  = record; drillLevel = DrillLevel.SOTTOCATEGORIA }
                                else -> {}
                            }
                        },
                        onEdit   = {
                            if (!isMutating) {
                                dialogTable    = currentTable
                                selectedRecord = record
                                showEditDialog = true
                            }
                        },
                        onDelete = {
                            if (!isMutating) {
                                dialogTable    = currentTable
                                selectedRecord = record
                                showDeleteConfirm = true
                            }
                        },
                        onToggleAttivo = { newVal ->
                            if (!isMutating) {
                                loadingRecordId = thisId
                                isMutating = true
                                scope.launch {
                                    try {
                                        when (currentTable) {
                                            ConfigTable.TIPOLOGIA,
                                            ConfigTable.CATEGORIA,
                                            ConfigTable.SOTTOCATEGORIA -> {
                                                val rowsToUpdate = findUtcsRowsForRecord(record, currentTable)
                                                rowsToUpdate.forEach { utcsRow ->
                                                    val utcsId = (utcsRow["id"] as? Double)?.toInt()
                                                        ?: utcsRow["id"].toString().toIntOrNull()
                                                        ?: return@forEach
                                                    api.updateRecord(
                                                        GenericUpdateRequest(
                                                            resource = ConfigTable.UTCS.resource,
                                                            id       = utcsId,
                                                            data     = mapOf("attivo" to newVal).sanitizeForSheet()
                                                        )
                                                    )
                                                }
                                            }
                                            ConfigTable.CONTO -> {
                                                val contoId = (record["id"] as? Double)?.toInt()
                                                    ?: record["id"]?.toString()?.toIntOrNull()
                                                if (contoId != null) {
                                                    api.updateRecord(
                                                        GenericUpdateRequest(
                                                            resource = ConfigTable.CONTO.resource,
                                                            id = contoId,
                                                            data = mapOf("attivo" to newVal).sanitizeForSheet()
                                                        )
                                                    )
                                                }
                                                val rowsToUpdate = findUcRowsForRecord(record)
                                                rowsToUpdate.forEach { ucRow ->
                                                    val ucId = (ucRow["id"] as? Double)?.toInt()
                                                        ?: ucRow["id"].toString().toIntOrNull()
                                                        ?: return@forEach
                                                    api.updateRecord(
                                                        GenericUpdateRequest(
                                                            resource = ConfigTable.UC.resource,
                                                            id       = ucId,
                                                            data     = mapOf("attivo" to newVal).sanitizeForSheet()
                                                        )
                                                    )
                                                }
                                            }
                                            else -> Unit
                                        }
                                        refreshCurrentLevel(showTopLoading = false)
                                    } catch (e: Exception) { errorMsg = e.message }
                                    finally { loadingRecordId = null; isMutating = false }

                                }
                            }
                        }
                    )
                }
            }
        }
    }



    if (isMutating) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxSize()
            ) {}
            Card {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Aggiornamento in corso…")
                }
            }
        }
    }
}

// ── Card singolo record ───────────────────────────────────────────────
@Composable
private fun ConfigRecordCard(
    table: ConfigTable,
    record: Map<String, Any?>,
    isDrillable: Boolean,
    isLoading: Boolean,
    onDrillDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleAttivo: (Boolean) -> Unit
) {
    val id = when (val raw = record["id"]) {
        is Double -> raw.toInt()
        is Int    -> raw
        is Long   -> raw.toInt()
        is String -> raw.toDoubleOrNull()?.toInt() ?: raw.toIntOrNull()
        else      -> null
    }
    val attivoKey = if (table == ConfigTable.CATEGORIA) "attiva" else "attivo"
    val attivoRaw = record[attivoKey]
    val attivo    = attivoRaw == true || attivoRaw?.toString()?.lowercase() == "true"

    val mainText = when (table) {
        ConfigTable.TIPOLOGIA      -> "${record["descrizione"] ?: "—"} [${record["tipo_movimento"]}]"
        ConfigTable.CATEGORIA      -> "${record["descrizione"] ?: "—"}"
        ConfigTable.SOTTOCATEGORIA -> "${record["descrizione"] ?: "—"}"
        ConfigTable.CONTO          -> "${record["descrizione"] ?: "—"}"
        ConfigTable.UC             -> "${record["id_utente"] ?: "—"} → ${record["id_conto"] ?: "—"}"
        ConfigTable.UTCS           -> "${record["id_tipologia"] ?: "—"} / ${record["id_categoria"] ?: "—"} / ${record["id_sottocategoria"] ?: "—"}"
    }

    ElevatedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .then(if (isDrillable && !isLoading) Modifier.clickable { onDrillDown() } else Modifier),
        colors    = CardDefaults.elevatedCardColors(
            containerColor = if (attivo) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.elevatedCardElevation(1.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(mainText,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isDrillable) FontWeight.Medium else FontWeight.Normal,
                    color      = if (attivo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (attivo) "Attivo" else "Disattivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (attivo) Brand else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Loading spinner sulla card durante operazione
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(24.dp).padding(end = 4.dp),
                    strokeWidth = 2.dp,
                    color       = Brand
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked         = attivo,
                        onCheckedChange = onToggleAttivo,
                        modifier        = Modifier.padding(end = 4.dp),
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor = Brand,
                            checkedTrackColor = Brand.copy(alpha = 0.4f)
                        )
                    )
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifica",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Elimina",
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp))
                    }
                    if (isDrillable) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Apri",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ── Helper condiviso (usato anche nel dialog) ────────────────────────
private fun extractIdStatic(v: Any?): String {
    val raw = (v as? Double)?.toInt()?.toString() ?: v?.toString() ?: ""
    return raw.split(" - ").first().trim()
}

// ── Dialog aggiungi / modifica ────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigRecordDialog(
    table: ConfigTable,
    record: Map<String, Any?>?,
    currentUtente: String,
    allCategorie: List<Map<String, Any?>>,
    allConti: List<Map<String, Any?>>,
    allTipologie: List<Map<String, Any?>>,
    allSottocategorie: List<Map<String, Any?>>,
    preselectedTipoId: String? = null,
    preselectedCatId:  String? = null,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any?>) -> Unit
) {
    val isNew = record == null

    var descrizione      by remember { mutableStateOf(record?.get("descrizione")?.toString() ?: "") }
    var tipoMovimento    by remember { mutableStateOf(record?.get("tipo_movimento")?.toString() ?: "uscita") }
    // idCategoria: se preselectedCatId è solo un numero, cerca il label completo "id - desc"
    val preselectedCatLabel = preselectedCatId?.let { pid ->
        allCategorie.firstOrNull { extractIdStatic(it["id"]) == pid }
            ?.let { cat -> "${extractIdStatic(cat["id"])} - ${cat["descrizione"]}" }
            ?: pid
    }
    var idCategoria      by remember { mutableStateOf(record?.get("id_categoria")?.toString() ?: preselectedCatLabel ?: "") }
    var idConto          by remember { mutableStateOf(record?.get("id_conto")?.toString() ?: "") }
    var idTipologia      by remember { mutableStateOf(record?.get("id_tipologia")?.toString() ?: preselectedTipoId ?: "") }
    var idSottocategoria by remember { mutableStateOf(record?.get("id_sottocategoria")?.toString() ?: "") }

    var tipoMovExpanded  by remember { mutableStateOf(false) }
    var catExpanded      by remember { mutableStateOf(false) }
    var contoExpanded    by remember { mutableStateOf(false) }
    var tipoExpanded     by remember { mutableStateOf(false) }
    var sottoExpanded    by remember { mutableStateOf(false) }
    var attemptedSubmit  by remember { mutableStateOf(false) }

    val descrizioneRequired = table == ConfigTable.TIPOLOGIA ||
            table == ConfigTable.CATEGORIA ||
            table == ConfigTable.SOTTOCATEGORIA ||
            table == ConfigTable.CONTO
    val categoriaRequired = table == ConfigTable.SOTTOCATEGORIA && preselectedCatId == null
    val descrizioneError = attemptedSubmit && descrizioneRequired && descrizione.isBlank()
    val categoriaError = attemptedSubmit && categoriaRequired && idCategoria.isBlank()
    val hasValidationErrors = descrizioneError || categoriaError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuovo — ${table.label}" else "Modifica record") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (table) {

                    ConfigTable.TIPOLOGIA -> {
                        OutlinedTextField(value = descrizione, onValueChange = { descrizione = it },
                            label = { Text("Descrizione *") },
                            isError = descrizioneError,
                            supportingText = { if (descrizioneError) Text("Campo obbligatorio") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        ExposedDropdownMenuBox(expanded = tipoMovExpanded, onExpandedChange = { tipoMovExpanded = !tipoMovExpanded }) {
                            OutlinedTextField(value = tipoMovimento, onValueChange = {}, readOnly = true,
                                label = { Text("Tipo movimento") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tipoMovExpanded) })
                            ExposedDropdownMenu(expanded = tipoMovExpanded, onDismissRequest = { tipoMovExpanded = false }) {
                                listOf("uscita", "entrata").forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) },
                                        onClick = { tipoMovimento = opt; tipoMovExpanded = false })
                                }
                            }
                        }
                    }

                    ConfigTable.CATEGORIA -> {
                        OutlinedTextField(value = descrizione, onValueChange = { descrizione = it },
                            label = { Text("Descrizione *") },
                            isError = descrizioneError,
                            supportingText = { if (descrizioneError) Text("Campo obbligatorio") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }

                    ConfigTable.SOTTOCATEGORIA -> {
                        // Categoria pre-selezionata dal contesto drill-down (read-only se preselezionata)
                        if (preselectedCatId != null && isNew) {
                            val catLabel = allCategorie.firstOrNull {
                                it["id"]?.toString()?.trimEnd('0')?.trimEnd('.') == preselectedCatId
                            }?.get("descrizione")?.toString() ?: preselectedCatId
                            OutlinedTextField(
                                value         = catLabel,
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text("Categoria") },
                                modifier      = Modifier.fillMaxWidth(),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor       = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor     = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor      = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                enabled = false
                            )
                        } else {
                            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                                OutlinedTextField(value = idCategoria.ifBlank { "Seleziona categoria…" },
                                    onValueChange = {}, readOnly = true, label = { Text("Categoria") },
                                    isError = categoriaError,
                                    supportingText = { if (categoriaError) Text("Campo obbligatorio") },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) })
                                ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                    allCategorie.forEach { cat ->
                                        val lbl = "${cat["id"]} - ${cat["descrizione"]}"
                                        DropdownMenuItem(text = { Text(lbl) },
                                            onClick = { idCategoria = lbl; catExpanded = false })
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = descrizione, onValueChange = { descrizione = it },
                            label = { Text("Descrizione *") },
                            isError = descrizioneError,
                            supportingText = { if (descrizioneError) Text("Campo obbligatorio") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }

                    ConfigTable.CONTO -> {
                        OutlinedTextField(value = descrizione, onValueChange = { descrizione = it },
                            label = { Text("Descrizione *") },
                            isError = descrizioneError,
                            supportingText = { if (descrizioneError) Text("Campo obbligatorio") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }

                    ConfigTable.UC -> {
                        Text("Utente: $currentUtente", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExposedDropdownMenuBox(expanded = contoExpanded, onExpandedChange = { contoExpanded = !contoExpanded }) {
                            OutlinedTextField(value = idConto.ifBlank { "Seleziona conto…" },
                                onValueChange = {}, readOnly = true, label = { Text("Conto") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(contoExpanded) })
                            ExposedDropdownMenu(expanded = contoExpanded, onDismissRequest = { contoExpanded = false }) {
                                allConti.forEach { conto ->
                                    val lbl = "${conto["id"]} - ${conto["descrizione"]}"
                                    DropdownMenuItem(text = { Text(lbl) },
                                        onClick = { idConto = lbl; contoExpanded = false })
                                }
                            }
                        }
                    }

                    ConfigTable.UTCS -> {
                        Text("Utente: $currentUtente", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExposedDropdownMenuBox(expanded = tipoExpanded, onExpandedChange = { tipoExpanded = !tipoExpanded }) {
                            OutlinedTextField(value = idTipologia.ifBlank { "Seleziona tipologia…" },
                                onValueChange = {}, readOnly = true, label = { Text("Tipologia") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tipoExpanded) })
                            ExposedDropdownMenu(expanded = tipoExpanded, onDismissRequest = { tipoExpanded = false }) {
                                allTipologie.forEach { tip ->
                                    val lbl = "${tip["id"]} - ${tip["descrizione"]}"
                                    DropdownMenuItem(text = { Text(lbl) },
                                        onClick = { idTipologia = lbl; tipoExpanded = false })
                                }
                            }
                        }
                        ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                            OutlinedTextField(value = idCategoria.ifBlank { "Seleziona categoria…" },
                                onValueChange = {}, readOnly = true, label = { Text("Categoria") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) })
                            ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                allCategorie.forEach { cat ->
                                    val lbl = "${cat["id"]} - ${cat["descrizione"]}"
                                    DropdownMenuItem(text = { Text(lbl) },
                                        onClick = { idCategoria = lbl; catExpanded = false })
                                }
                            }
                        }
                        ExposedDropdownMenuBox(expanded = sottoExpanded, onExpandedChange = { sottoExpanded = !sottoExpanded }) {
                            OutlinedTextField(value = idSottocategoria.ifBlank { "Seleziona sottocategoria…" },
                                onValueChange = {}, readOnly = true, label = { Text("Sottocategoria") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sottoExpanded) })
                            ExposedDropdownMenu(expanded = sottoExpanded, onDismissRequest = { sottoExpanded = false }) {
                                allSottocategorie.forEach { sotto ->
                                    val lbl = "${sotto["id"]} - ${sotto["descrizione"]}"
                                    DropdownMenuItem(text = { Text(lbl) },
                                        onClick = { idSottocategoria = lbl; sottoExpanded = false })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attemptedSubmit = true
                if (hasValidationErrors) return@TextButton
                // Payload pulito — solo i campi editabili della tabella
                val payload: Map<String, Any?> = when (table) {
                    ConfigTable.TIPOLOGIA      -> mapOf(
                        "descrizione"    to descrizione.trim(),
                        "tipo_movimento" to tipoMovimento,
                        "attivo"         to true
                    )
                    ConfigTable.CATEGORIA      -> mapOf(
                        "descrizione" to descrizione.trim(),
                        "attiva"      to true
                    )
                    ConfigTable.SOTTOCATEGORIA -> mapOf(
                        "id_categoria" to idCategoria.trim(),
                        "descrizione"  to descrizione.trim(),
                        "attivo"       to true
                    )
                    ConfigTable.CONTO          -> mapOf(
                        "descrizione" to descrizione.trim(),
                        "attivo"      to true
                    )
                    ConfigTable.UC             -> mapOf(
                        "id_utente" to currentUtente,
                        "id_conto"  to idConto.trim(),
                        "attivo"    to true
                    )
                    ConfigTable.UTCS           -> mapOf(
                        "id_utente"         to currentUtente,
                        "id_tipologia"      to idTipologia.trim(),
                        "id_categoria"      to idCategoria.trim(),
                        "id_sottocategoria" to idSottocategoria.trim(),
                        "attivo"            to true
                    )
                }
                onSave(payload)
            }) { Text("Salva", color = Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
