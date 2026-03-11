package com.emanuele.gestionespese.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.ui.theme.ExpenseContainer
import com.emanuele.gestionespese.ui.theme.IncomeContainer
import com.emanuele.gestionespese.ui.theme.OnExpense
import com.emanuele.gestionespese.ui.theme.OnIncome
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpesaFormScreen(
    vm: SpeseViewModel,
    editingId: Int,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    val editingSpesa: SpesaView? = remember(state.spese, editingId) {
        if (editingId == -1) null else state.spese.firstOrNull { it.id == editingId }
    }

    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val isExisting = editingId != -1
    val saving = state.saving

    var editEnabled by remember(editingId) { mutableStateOf(editingId == -1) }

    val initialData = remember(editingSpesa) { editingSpesa?.data ?: LocalDate.now().format(formatter) }
    val initialImporto = remember(editingSpesa) {
        editingSpesa?.importo?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: ""
    }
    val initialTipo = remember(editingSpesa) { editingSpesa?.tipo.orEmpty() }
    val initialConto = remember(editingSpesa) { editingSpesa?.conto.orEmpty() }
    val initialNote = remember(editingSpesa) { editingSpesa?.descrizione.orEmpty() }
    val initialCategoria = remember(editingSpesa) { editingSpesa?.categoria?.trim().orEmpty() }
    val initialSottocategoria = remember(editingSpesa) { editingSpesa?.sottocategoria?.trim().orEmpty() }

    var data by remember(editingSpesa) { mutableStateOf(initialData) }
    var importoText by remember(editingSpesa) { mutableStateOf(initialImporto) }
    var tipo by remember(editingSpesa) { mutableStateOf(initialTipo) }
    var conto by remember(editingSpesa) { mutableStateOf(initialConto) }
    var note by remember(editingSpesa) { mutableStateOf(initialNote) }
    var categoria by remember(editingSpesa) { mutableStateOf(initialCategoria) }
    var sottocategoria by remember(editingSpesa) { mutableStateOf(initialSottocategoria) }

    // Prefill da Draft (solo per nuova spesa)
    LaunchedEffect(editingId, state.draftPrefillTick) {
        if (editingId == -1 && state.draftPrefillTick != 0L) {
            editEnabled = true
            state.draftData?.let { data = it }
            state.draftImporto?.let { importoText = String.format(Locale.getDefault(), "%.2f", it) }
            conto = state.draftMetodo.orEmpty()
            note = state.draftDescrizione.orEmpty()
        }
    }

    LaunchedEffect(Unit) { vm.loadLookupsIfNeeded() }

    // Reset a cascata
    LaunchedEffect(tipo) { categoria = ""; sottocategoria = "" }
    LaunchedEffect(categoria) { sottocategoria = "" }

    var showErrorPopup by remember { mutableStateOf(false) }
    var errorPopupText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank()) { errorPopupText = err; showErrorPopup = true }
    }

    LaunchedEffect(state.saveOkTick) {
        if (state.saveOkTick != 0L) { vm.consumeSaveOk(); editEnabled = false; onBack() }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val importoValue = importoText.replace(",", ".").toDoubleOrNull()

    val canSave = editEnabled && !saving &&
            importoValue != null && importoValue > 0.0 &&
            data.isNotBlank() && tipo.isNotBlank() &&
            conto.isNotBlank() && categoria.isNotBlank()

    // UTCS filtrati per utenza corrente (viene dallo state del ViewModel)
    val utcsUserActive = remember(state.utcs) {
        state.utcs.filter { it.attivo }
    }

    val tipiOptions = remember(utcsUserActive) {
        utcsUserActive.map { it.tipologia.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val categorieOptions = remember(utcsUserActive, tipo) {
        if (tipo.isBlank()) emptyList()
        else utcsUserActive
            .filter { it.tipologia.trim().equals(tipo.trim(), ignoreCase = true) }
            .map { it.categoria.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val sottocategorieOptions = remember(utcsUserActive, tipo, categoria) {
        if (tipo.isBlank() || categoria.isBlank()) emptyList()
        else utcsUserActive
            .filter { it.tipologia.trim().equals(tipo.trim(), ignoreCase = true) }
            .filter { it.categoria.trim().equals(categoria.trim(), ignoreCase = true) }
            .map { it.sottocategoria.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val contiOptions = remember(state.conti) {
        state.conti.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    // --- Dialogs ---

    if (showErrorPopup) {
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { showErrorPopup = false; vm.clearError() },
            title = { Text("Errore durante il salvataggio") },
            text = {
                Column {
                    Text(errorPopupText ?: "Errore sconosciuto")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Puoi copiare il dettaglio per analizzarlo.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("errore", errorPopupText ?: ""))
                            )
                        }
                    }) { Text("Copia") }
                    TextButton(onClick = { showErrorPopup = false; vm.clearError() }) { Text("Chiudi") }
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        data = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(formatter)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare questa spesa?") },
            text = { Text("L'operazione è definitiva.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    if (isExisting) vm.delete(editingId)
                    onBack()
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") }
            }
        )
    }

    // --- Scaffold ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isExisting) "Dettaglio spesa" else "Nuova spesa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (isExisting) {
                        IconButton(onClick = { editEnabled = !editEnabled }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifica")
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = editEnabled) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                vm.saveSpesa(
                                    editingId = if (isExisting) editingId else null,
                                    data = data,
                                    importo = importoValue ?: 0.0,
                                    tipo = tipo,
                                    conto = conto,
                                    descrizione = note.trim().ifBlank { null },
                                    categoria = categoria.trim(),
                                    sottocategoria = sottocategoria.trim().ifBlank { null }
                                )
                            },
                            enabled = canSave,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Salvataggio…")
                            } else {
                                Text("Salva")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                data = initialData; importoText = initialImporto
                                tipo = initialTipo; conto = initialConto; note = initialNote
                                categoria = initialCategoria; sottocategoria = initialSottocategoria
                                editEnabled = false
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !saving
                        ) { Text("Annulla") }
                    }
                }
            }
        }
    ) { padding ->

        val isEntrata = tipo.contains("entrata", ignoreCase = true)
        val importoHeader = importoValue ?: (editingSpesa?.importo ?: 0.0)
        val headerContainer = if (isEntrata) IncomeContainer else ExpenseContainer
        val headerOn = if (isEntrata) OnIncome else OnExpense

        Column(
            modifier = Modifier
                .padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // Card riepilogo
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = headerContainer),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f €", importoHeader),
                            style = MaterialTheme.typography.headlineSmall,
                            color = headerOn
                        )
                        AssistChip(
                            onClick = { },
                            label = { Text(tipo.ifBlank { "Tipologia n/d" }) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { }, label = { Text(conto.ifBlank { "Conto n/d" }) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)))
                        AssistChip(onClick = { }, label = { Text(data.ifBlank { "Data n/d" }) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)))
                    }
                }
            }

            // Card campi
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.loadingLookups) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                    OutlinedTextField(
                        value = data, onValueChange = { }, readOnly = true,
                        enabled = editEnabled && !saving, label = { Text("Data") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { if (editEnabled && !saving) showDatePicker = true },
                                enabled = editEnabled && !saving
                            ) { Icon(Icons.Default.DateRange, contentDescription = "Cambia data") }
                        }
                    )

                    OutlinedTextField(
                        value = importoText,
                        onValueChange = { if (editEnabled && !saving) importoText = it },
                        label = { Text("Importo (€)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        readOnly = !editEnabled || saving, enabled = editEnabled && !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    DropdownFieldString("Tipologia", tipo, tipiOptions, editEnabled && !saving && !state.loadingLookups) { tipo = it }
                    DropdownFieldString("Categoria", categoria, categorieOptions, editEnabled && !saving && !state.loadingLookups) { categoria = it; sottocategoria = "" }
                    DropdownFieldString("Sottocategoria (opz.)", sottocategoria, sottocategorieOptions,
                        editEnabled && !saving && !state.loadingLookups && categoria.isNotBlank()) { sottocategoria = it }
                    DropdownFieldString("Conto", conto, contiOptions, editEnabled && !saving && !state.loadingLookups) { conto = it }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (editEnabled && !saving) note = it },
                        label = { Text("Note (opzionale)") }, modifier = Modifier.fillMaxWidth(),
                        readOnly = !editEnabled || saving, enabled = editEnabled && !saving, minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            AnimatedVisibility(visible = editEnabled && isExisting && !saving) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Elimina") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownFieldString(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(
            value = value.ifBlank { "Seleziona…" },
            onValueChange = { }, readOnly = true, enabled = enabled,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onPick(opt); expanded = false })
            }
        }
    }
}