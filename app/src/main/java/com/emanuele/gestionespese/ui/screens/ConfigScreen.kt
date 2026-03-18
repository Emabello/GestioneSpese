/**
 * ConfigScreen.kt
 *
 * Schermata di configurazione delle lookup tables: tipi, categorie, conti
 * e sottocategorie. Permette all'utente di visualizzare i dati sincronizzati
 * e forzare una risincronizzazione manuale dal backend.
 *
 * I dati visualizzati sono quelli presenti nel DB locale Room, aggiornati
 * dall'ultima sincronizzazione avvenuta.
 */
package com.emanuele.gestionespese.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.model.*
import com.emanuele.gestionespese.data.repo.stripFormulaFields
import com.emanuele.gestionespese.ui.theme.Brand
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

// ── Tabelle disponibili ───────────────────────────────────────────────
enum class ConfigTable(val label: String, val resource: String) {
    TIPOLOGIA("Tipologie",     "tipologia"),
    CATEGORIA("Categorie",     "categoria"),
    SOTTOCATEGORIA("Sottocategorie", "sottocategoria"),
    CONTO("Conti",             "conto"),
    UC("Conti utente (UC)",    "uc"),
    UTCS("Combinazioni (UTCS)","utcs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app     = context.applicationContext as MyApp
    val api     = app.api
    val scope   = rememberCoroutineScope()
    val currentUtente = app.currentUserLabel ?: ""

    // ── Stato ────────────────────────────────────────────────────────
    var selectedTable   by remember { mutableStateOf(ConfigTable.TIPOLOGIA) }
    var tableExpanded   by remember { mutableStateOf(false) }
    var records         by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMsg        by remember { mutableStateOf<String?>(null) }

    // Dialog stato
    var showAddDialog     by remember { mutableStateOf(false) }
    var showEditDialog    by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedRecord    by remember { mutableStateOf<Map<String, Any?>?>(null) }

    // Dati aggiuntivi per dropdown nei dialog
    var allCategorie    by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allConti        by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allTipologie    by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allSottocategorie by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    // Dati UC e UTCS caricati per la logica di cascade (non visibili come tab)
    var allUc   by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var allUtcs by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    // Messaggio cascade (quante voci sono state disattivate)
    var cascadeMsg by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Carica records ───────────────────────────────────────────────
    fun loadRecords() {
        scope.launch {
            isLoading = true
            errorMsg  = null
            try {
                records = when (selectedTable) {
                    ConfigTable.UC   -> api.getUc(utente = currentUtente).data ?: emptyList()
                    ConfigTable.UTCS -> api.getUtcs().data
                        ?.filter { r -> java.lang.String(r["id_utente"]?.toString() ?: "").trim() == currentUtente }
                        ?: emptyList()
                    ConfigTable.CATEGORIA -> api.getCategorie().data
                        ?.map { mapOf("id" to it.id, "descrizione" to it.descrizione, "attiva" to it.attiva) }
                        ?: emptyList()
                    ConfigTable.TIPOLOGIA -> api.getTipi().data ?: emptyList()
                    ConfigTable.SOTTOCATEGORIA -> api.getSottocategorie().data ?: emptyList()
                    ConfigTable.CONTO -> api.getConti().data ?: emptyList()
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // Carica dati ausiliari per i dialog e per la cascade
    fun loadAuxData() {
        scope.launch {
            try {
                val categorieDeferred      = async { api.getCategorie().data?.map { mapOf("id" to it.id, "descrizione" to it.descrizione) } ?: emptyList() }
                val contiDeferred          = async { api.getConti().data ?: emptyList() }
                val tipologieDeferred      = async { api.getTipi().data ?: emptyList() }
                val sottocDeferred         = async { api.getSottocategorie().data ?: emptyList() }
                val ucDeferred             = async { api.getUc(utente = currentUtente).data ?: emptyList() }
                val utcsDeferred           = async {
                    api.getUtcs().data
                        ?.filter { r -> java.lang.String(r["id_utente"]?.toString() ?: "").trim() == currentUtente }
                        ?: emptyList()
                }
                allCategorie      = categorieDeferred.await()
                allConti          = contiDeferred.await()
                allTipologie      = tipologieDeferred.await()
                allSottocategorie = sottocDeferred.await()
                allUc             = ucDeferred.await()
                allUtcs           = utcsDeferred.await()
            } catch (_: Exception) { }
        }
    }

    // Disattiva in cascata le voci correlate quando si disattiva un record
    fun cascadeDeactivate(table: ConfigTable, recordId: Int) {
        scope.launch {
            try {
                fun normalizeId(v: Any?): String =
                    (v as? Double)?.toInt()?.toString() ?: v?.toString() ?: ""

                val idStr = recordId.toString()

                // Determina quali record UC/UTCS vanno disattivati
                val toDeactivateUtcs = when (table) {
                    ConfigTable.TIPOLOGIA     -> allUtcs.filter { normalizeId(it["id_tipologia"])     == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                    ConfigTable.CATEGORIA     -> allUtcs.filter { normalizeId(it["id_categoria"])     == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                    ConfigTable.SOTTOCATEGORIA -> allUtcs.filter { normalizeId(it["id_sottocategoria"]) == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                    else -> emptyList()
                }
                val toDeactivateSottocat = when (table) {
                    ConfigTable.CATEGORIA -> allSottocategorie.filter { normalizeId(it["id_categoria"]) == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                    else -> emptyList()
                }
                val toDeactivateUc = when (table) {
                    ConfigTable.CONTO -> allUc.filter { normalizeId(it["id_conto"]) == idStr && (it["attivo"] == true || it["attivo"]?.toString()?.lowercase() == "true") }
                    else -> emptyList()
                }

                val totalCount = toDeactivateUtcs.size + toDeactivateSottocat.size + toDeactivateUc.size
                if (totalCount == 0) return@launch

                // Disattiva in parallelo
                val jobs = mutableListOf<kotlinx.coroutines.Deferred<*>>()
                toDeactivateUtcs.forEach { r ->
                    val rid = (r["id"] as? Double)?.toInt() ?: r["id"].toString().toIntOrNull() ?: return@forEach
                    jobs += async { api.updateRecord(GenericUpdateRequest(resource = ConfigTable.UTCS.resource, id = rid, data = mapOf("attivo" to false))) }
                }
                toDeactivateSottocat.forEach { r ->
                    val rid = (r["id"] as? Double)?.toInt() ?: r["id"].toString().toIntOrNull() ?: return@forEach
                    jobs += async { api.updateRecord(GenericUpdateRequest(resource = ConfigTable.SOTTOCATEGORIA.resource, id = rid, data = mapOf("attivo" to false))) }
                }
                toDeactivateUc.forEach { r ->
                    val rid = (r["id"] as? Double)?.toInt() ?: r["id"].toString().toIntOrNull() ?: return@forEach
                    jobs += async { api.updateRecord(GenericUpdateRequest(resource = ConfigTable.UC.resource, id = rid, data = mapOf("attivo" to false))) }
                }
                jobs.awaitAll()

                cascadeMsg = "$totalCount ${if (totalCount == 1) "voce correlata disattivata" else "voci correlate disattivate"}"
                loadAuxData()
            } catch (e: Exception) {
                errorMsg = e.message
            }
        }
    }

    LaunchedEffect(selectedTable) {
        loadRecords()
        loadAuxData()
    }

    LaunchedEffect(cascadeMsg) {
        cascadeMsg?.let {
            snackbarHostState.showSnackbar(it)
            cascadeMsg = null
        }
    }

    // ── Dialog Aggiungi / Modifica ────────────────────────────────────
    if (showAddDialog || showEditDialog) {
        ConfigRecordDialog(
            table         = selectedTable,
            record        = if (showEditDialog) selectedRecord else null,
            currentUtente = currentUtente,
            allCategorie  = allCategorie,
            allConti      = allConti,
            allTipologie  = allTipologie,
            allSottocategorie = allSottocategorie,
            onDismiss     = { showAddDialog = false; showEditDialog = false },
            onSave        = { data ->
                scope.launch {
                    try {
                        if (showEditDialog && selectedRecord != null) {
                            val id = (selectedRecord!!["id"] as? Double)?.toInt()
                                ?: selectedRecord!!["id"].toString().toIntOrNull() ?: return@launch
                            api.updateRecord(
                                GenericUpdateRequest(
                                    resource = selectedTable.resource,
                                    id       = id,
                                    data     = data.stripFormulaFields()
                                )
                            )
                        } else {
                            api.insertRecord(
                                GenericInsertRequest(
                                    resource = selectedTable.resource,
                                    data     = data.stripFormulaFields()
                                )
                            )
                        }
                        showAddDialog  = false
                        showEditDialog = false
                        loadRecords()
                    } catch (e: Exception) {
                        errorMsg = e.message
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
                    scope.launch {
                        try {
                            val id = (selectedRecord!!["id"] as? Double)?.toInt()
                                ?: selectedRecord!!["id"].toString().toIntOrNull() ?: return@launch
                            api.deleteRecord(
                                GenericDeleteRequest(resource = selectedTable.resource, id = id)
                            )
                            showDeleteConfirm = false
                            loadRecords()
                        } catch (e: Exception) {
                            errorMsg = e.message
                            showDeleteConfirm = false
                        }
                    }
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") }
            }
        )
    }

    // ── Scaffold ─────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione dati") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAddDialog = true },
                containerColor = Brand
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi", tint = androidx.compose.ui.graphics.Color.White)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Dropdown selezione tabella ────────────────────────────
            ExposedDropdownMenuBox(
                expanded        = tableExpanded,
                onExpandedChange = { tableExpanded = !tableExpanded }
            ) {
                OutlinedTextField(
                    value         = selectedTable.label,
                    onValueChange = { },
                    readOnly      = true,
                    label         = { Text("Tabella") },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tableExpanded) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor      = Brand,
                        focusedLabelColor       = Brand
                    )
                )
                ExposedDropdownMenu(
                    expanded        = tableExpanded,
                    onDismissRequest = { tableExpanded = false }
                ) {
                    ConfigTable.entries
                        .filter { it != ConfigTable.UC && it != ConfigTable.UTCS }
                        .forEach { table ->
                            DropdownMenuItem(
                                text    = { Text(table.label) },
                                onClick = {
                                    selectedTable  = table
                                    tableExpanded  = false
                                }
                            )
                        }
                }
            }

            // ── Errore ────────────────────────────────────────────────
            errorMsg?.let { err ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "Errore: $err",
                        modifier = Modifier.padding(12.dp),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ── Loading ───────────────────────────────────────────────
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = Brand
                )
            }

            // ── Contatore ─────────────────────────────────────────────
            Text(
                "${records.size} record",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Lista record ──────────────────────────────────────────
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding      = PaddingValues(bottom = 88.dp)
            ) {
                items(records) { record ->
                    ConfigRecordCard(
                        table    = selectedTable,
                        record   = record,
                        onEdit   = { selectedRecord = record; showEditDialog = true },
                        onDelete = { selectedRecord = record; showDeleteConfirm = true },
                        onToggleAttivo = { newVal ->
                            scope.launch {
                                try {
                                    val id = (record["id"] as? Double)?.toInt()
                                        ?: record["id"].toString().toIntOrNull() ?: return@launch
                                    val attivoKey = if (selectedTable == ConfigTable.CATEGORIA) "attiva" else "attivo"
                                    api.updateRecord(
                                        GenericUpdateRequest(
                                            resource = selectedTable.resource,
                                            id       = id,
                                            data     = mapOf(attivoKey to newVal)
                                        )
                                    )
                                    // Se si disattiva, propaga la disattivazione alle voci correlate
                                    if (!newVal) cascadeDeactivate(selectedTable, id)
                                    loadRecords()
                                } catch (e: Exception) {
                                    errorMsg = e.message
                                }
                            }
                        }
                    )
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleAttivo: (Boolean) -> Unit
) {
    val id          = (record["id"] as? Double)?.toInt() ?: record["id"]?.toString()?.toIntOrNull()
    val attivoKey   = if (table == ConfigTable.CATEGORIA) "attiva" else "attivo"
    val attivoRaw   = record[attivoKey]
    val attivo      = attivoRaw == true || attivoRaw?.toString()?.lowercase() == "true"

    // Testo principale in base alla tabella
    val mainText = when (table) {
        ConfigTable.TIPOLOGIA      -> "${id} - ${record["descrizione"] ?: "—"} [${record["tipo_movimento"] ?: "uscita"}]"
        ConfigTable.CATEGORIA      -> "${id} - ${record["descrizione"] ?: "—"}"
        ConfigTable.SOTTOCATEGORIA -> "${id} - ${record["descrizione"] ?: "—"} (cat: ${record["id_categoria"] ?: "—"})"
        ConfigTable.CONTO          -> "${id} - ${record["descrizione"] ?: "—"}"
        ConfigTable.UC             -> "${record["id_utente"] ?: "—"} → ${record["id_conto"] ?: "—"}"
        ConfigTable.UTCS           -> "${record["id_tipologia"] ?: "—"} / ${record["id_categoria"] ?: "—"} / ${record["id_sottocategoria"] ?: "—"}"
    }

    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
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
                Text(
                    mainText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (attivo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (attivo) "Attivo" else "Disattivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (attivo) Brand else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Toggle attivo
                Switch(
                    checked         = attivo,
                    onCheckedChange = onToggleAttivo,
                    modifier        = Modifier.padding(end = 4.dp),
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor = Brand,
                        checkedTrackColor = Brand.copy(alpha = 0.4f)
                    )
                )
                // Modifica
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Modifica",
                        tint   = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Elimina
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina",
                        tint   = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Dialog aggiungi / modifica ────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigRecordDialog(
    table: ConfigTable,
    record: Map<String, Any?>?,         // null = nuovo
    currentUtente: String,
    allCategorie: List<Map<String, Any?>>,
    allConti: List<Map<String, Any?>>,
    allTipologie: List<Map<String, Any?>>,
    allSottocategorie: List<Map<String, Any?>>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any?>) -> Unit
) {
    val isNew = record == null

    // Campi del form — inizializzati dal record esistente o vuoti
    var descrizione    by remember { mutableStateOf(record?.get("descrizione")?.toString() ?: "") }
    var tipoMovimento  by remember { mutableStateOf(record?.get("tipo_movimento")?.toString() ?: "uscita") }
    var idCategoria    by remember { mutableStateOf(record?.get("id_categoria")?.toString() ?: "") }
    var idConto        by remember { mutableStateOf(record?.get("id_conto")?.toString() ?: "") }
    var idTipologia    by remember { mutableStateOf(record?.get("id_tipologia")?.toString() ?: "") }
    var idSottocategoria by remember { mutableStateOf(record?.get("id_sottocategoria")?.toString() ?: "") }

    // Dropdown expanded states
    var tipoMovExpanded  by remember { mutableStateOf(false) }
    var catExpanded      by remember { mutableStateOf(false) }
    var contoExpanded    by remember { mutableStateOf(false) }
    var tipoExpanded     by remember { mutableStateOf(false) }
    var sottoExpanded    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isNew) "Nuovo record — ${table.label}" else "Modifica record")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                when (table) {

                    // ── TIPOLOGIA ─────────────────────────────────────
                    ConfigTable.TIPOLOGIA -> {
                        OutlinedTextField(
                            value         = descrizione,
                            onValueChange = { descrizione = it },
                            label         = { Text("Descrizione") },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true
                        )
                        // Dropdown tipo_movimento
                        ExposedDropdownMenuBox(
                            expanded        = tipoMovExpanded,
                            onExpandedChange = { tipoMovExpanded = !tipoMovExpanded }
                        ) {
                            OutlinedTextField(
                                value         = tipoMovimento,
                                onValueChange = { },
                                readOnly      = true,
                                label         = { Text("Tipo movimento") },
                                modifier      = Modifier.fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(tipoMovExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded        = tipoMovExpanded,
                                onDismissRequest = { tipoMovExpanded = false }
                            ) {
                                listOf("uscita", "entrata").forEach { opt ->
                                    DropdownMenuItem(
                                        text    = { Text(opt) },
                                        onClick = { tipoMovimento = opt; tipoMovExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // ── CATEGORIA ─────────────────────────────────────
                    ConfigTable.CATEGORIA -> {
                        OutlinedTextField(
                            value         = descrizione,
                            onValueChange = { descrizione = it },
                            label         = { Text("Descrizione") },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true
                        )
                    }

                    // ── SOTTOCATEGORIA ────────────────────────────────
                    ConfigTable.SOTTOCATEGORIA -> {
                        // Dropdown categoria
                        ExposedDropdownMenuBox(
                            expanded        = catExpanded,
                            onExpandedChange = { catExpanded = !catExpanded }
                        ) {
                            OutlinedTextField(
                                value         = idCategoria.ifBlank { "Seleziona categoria…" },
                                onValueChange = { },
                                readOnly      = true,
                                label         = { Text("Categoria") },
                                modifier      = Modifier.fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded        = catExpanded,
                                onDismissRequest = { catExpanded = false }
                            ) {
                                allCategorie.forEach { cat ->
                                    val label = "${cat["id"]} - ${cat["descrizione"]}"
                                    DropdownMenuItem(
                                        text    = { Text(label) },
                                        onClick = { idCategoria = label; catExpanded = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value         = descrizione,
                            onValueChange = { descrizione = it },
                            label         = { Text("Descrizione") },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true
                        )
                    }

                    // ── CONTO ─────────────────────────────────────────
                    ConfigTable.CONTO -> {
                        OutlinedTextField(
                            value         = descrizione,
                            onValueChange = { descrizione = it },
                            label         = { Text("Descrizione") },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true
                        )
                    }

                    // ── UC ────────────────────────────────────────────
                    ConfigTable.UC -> {
                        Text(
                            "Utente: $currentUtente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExposedDropdownMenuBox(
                            expanded        = contoExpanded,
                            onExpandedChange = { contoExpanded = !contoExpanded }
                        ) {
                            OutlinedTextField(
                                value         = idConto.ifBlank { "Seleziona conto…" },
                                onValueChange = { },
                                readOnly      = true,
                                label         = { Text("Conto") },
                                modifier      = Modifier.fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(contoExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded        = contoExpanded,
                                onDismissRequest = { contoExpanded = false }
                            ) {
                                allConti.forEach { conto ->
                                    val label = "${conto["id"]} - ${conto["descrizione"]}"
                                    DropdownMenuItem(
                                        text    = { Text(label) },
                                        onClick = { idConto = label; contoExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // ── UTCS ──────────────────────────────────────────
                    ConfigTable.UTCS -> {
                        Text(
                            "Utente: $currentUtente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Tipologia
                        ExposedDropdownMenuBox(
                            expanded        = tipoExpanded,
                            onExpandedChange = { tipoExpanded = !tipoExpanded }
                        ) {
                            OutlinedTextField(
                                value         = idTipologia.ifBlank { "Seleziona tipologia…" },
                                onValueChange = { },
                                readOnly      = true,
                                label         = { Text("Tipologia") },
                                modifier      = Modifier.fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(tipoExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded        = tipoExpanded,
                                onDismissRequest = { tipoExpanded = false }
                            ) {
                                allTipologie.forEach { tip ->
                                    val label = "${tip["id"]} - ${tip["descrizione"]}"
                                    DropdownMenuItem(
                                        text    = { Text(label) },
                                        onClick = { idTipologia = label; tipoExpanded = false }
                                    )
                                }
                            }
                        }
                        // Categoria
                        ExposedDropdownMenuBox(
                            expanded        = catExpanded,
                            onExpandedChange = { catExpanded = !catExpanded }
                        ) {
                            OutlinedTextField(
                                value         = idCategoria.ifBlank { "Seleziona categoria…" },
                                onValueChange = { },
                                readOnly      = true,
                                label         = { Text("Categoria") },
                                modifier      = Modifier.fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded        = catExpanded,
                                onDismissRequest = { catExpanded = false }
                            ) {
                                allCategorie.forEach { cat ->
                                    val label = "${cat["id"]} - ${cat["descrizione"]}"
                                    DropdownMenuItem(
                                        text    = { Text(label) },
                                        onClick = { idCategoria = label; catExpanded = false }
                                    )
                                }
                            }
                        }
                        // Sottocategoria
                        ExposedDropdownMenuBox(
                            expanded        = sottoExpanded,
                            onExpandedChange = { sottoExpanded = !sottoExpanded }
                        ) {
                            OutlinedTextField(
                                value         = idSottocategoria.ifBlank { "Seleziona sottocategoria…" },
                                onValueChange = { },
                                readOnly      = true,
                                label         = { Text("Sottocategoria") },
                                modifier      = Modifier.fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(sottoExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded        = sottoExpanded,
                                onDismissRequest = { sottoExpanded = false }
                            ) {
                                allSottocategorie.forEach { sotto ->
                                    val label = "${sotto["id"]} - ${sotto["descrizione"]}"
                                    DropdownMenuItem(
                                        text    = { Text(label) },
                                        onClick = { idSottocategoria = label; sottoExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Costruisce il payload in base alla tabella
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
                        "id_utente"        to currentUtente,
                        "id_tipologia"     to idTipologia.trim(),
                        "id_categoria"     to idCategoria.trim(),
                        "id_sottocategoria" to idSottocategoria.trim(),
                        "attivo"           to true
                    )
                }
                onSave(payload)
            }) { Text("Salva", color = Brand) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}