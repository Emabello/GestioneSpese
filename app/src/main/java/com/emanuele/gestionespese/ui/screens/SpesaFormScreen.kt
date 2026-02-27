package com.emanuele.gestionespese.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.Categoria
import com.emanuele.gestionespese.data.model.Sottocategoria
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.ui.theme.ExpenseContainer
import com.emanuele.gestionespese.ui.theme.IncomeContainer
import com.emanuele.gestionespese.ui.theme.OnExpense
import com.emanuele.gestionespese.ui.theme.OnIncome
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpesaFormScreen(
    vm: SpeseViewModel,
    editingId: Int,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    val catLabel: (Categoria) -> String = { it.nome }
    val catId: (Categoria) -> String? = { it.id }

    val subLabel: (Sottocategoria) -> String = { it.nome }
    val subId: (Sottocategoria) -> String? = { it.id }

    val tipi = listOf("uscita", "entrata")
    val metodi = listOf("Webank")

    val categorie: List<Categoria> = state.categorie
    val sottocategorie: List<Sottocategoria> = state.sottocategorie

    val editingSpesa: SpesaView? = remember(state.spese, editingId) {
        if (editingId == -1) null else state.spese.firstOrNull { it.id == editingId }
    }

    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    // Default: se è nuova -> edit subito; se è esistente -> read-only finché non premi Modifica
    var editEnabled by remember(editingId) { mutableStateOf(editingId == -1) }

    val initialData = remember(editingSpesa) { editingSpesa?.data ?: LocalDate.now().format(formatter) }
    val initialImporto = remember(editingSpesa) {
        editingSpesa?.importo?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: ""
    }
    val initialTipo = remember(editingSpesa) { editingSpesa?.tipo ?: "uscita" }
    val initialMetodo = remember(editingSpesa) { editingSpesa?.metodoPagamento ?: metodi.first() }
    val initialNote = remember(editingSpesa) { editingSpesa?.descrizione ?: "" }

    val initialCategoriaId = remember(editingSpesa) { editingSpesa?.categoriaId }
    val initialSottocategoriaId = remember(editingSpesa) { editingSpesa?.sottocategoriaId }

    var data by remember(editingSpesa) { mutableStateOf(initialData) }
    var importoText by remember(editingSpesa) { mutableStateOf(initialImporto) }
    var tipo by remember(editingSpesa) { mutableStateOf(initialTipo) }
    var metodo by remember(editingSpesa) { mutableStateOf(initialMetodo) }
    var note by remember(editingSpesa) { mutableStateOf(initialNote) }

    // ✅ PREFILL da Draft (solo per nuova spesa)
    LaunchedEffect(editingId, state.draftPrefillTick) {
        if (editingId == -1 && state.draftPrefillTick != 0L) {

            // abilita edit (così puoi salvare subito)
            editEnabled = true

            // data
            state.draftData?.let { data = it }

            // importo (format italiano con virgola)
            state.draftImporto?.let { imp ->
                importoText = String.format(Locale.getDefault(), "%.2f", imp)
            }

            // metodo fisso Webank (o quello del draft)
            metodo = state.draftMetodo ?: "Webank"

            // descrizione -> nel tuo form è "note"
            note = state.draftDescrizione.orEmpty()
        }
    }

    var categoriaId by remember(editingSpesa) { mutableStateOf<String?>(initialCategoriaId) }
    var sottocategoriaId by remember(editingSpesa) { mutableStateOf<String?>(initialSottocategoriaId) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Popup errore (UI)
    var showErrorPopup by remember { mutableStateOf(false) }
    var errorPopupText by remember { mutableStateOf<String?>(null) }

    val isExisting = editingId != -1
    val saving = state.saving

    val importoValue = importoText.replace(",", ".").toDoubleOrNull()
    val canSave =
        editEnabled &&
                !saving &&
                importoValue != null &&
                importoValue > 0.0 &&
                data.isNotBlank() &&
                categoriaId != null

    // Lookup
    LaunchedEffect(Unit) { vm.loadCategorie() }

    LaunchedEffect(categoriaId) {
        val cid = categoriaId ?: return@LaunchedEffect
        vm.loadSottocategorie(cid)
    }

    // Se arriva un errore dal VM, apro il popup (e NON esco dalla schermata)
    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank()) {
            errorPopupText = err
            showErrorPopup = true
        }
    }

    // Esci SOLO su successo: quando saveOkTick cambia
    LaunchedEffect(state.saveOkTick) {
        if (state.saveOkTick != 0L) {
            vm.consumeSaveOk()
            editEnabled = false
            onBack()
        }
    }

    // Popup errore
    if (showErrorPopup) {

        val clipboardManager = LocalClipboardManager.current

        AlertDialog(
            onDismissRequest = {
                showErrorPopup = false
                vm.clearError()
            },
            title = {
                Text(
                    text = "Errore durante il salvataggio",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = errorPopupText ?: "Errore sconosciuto",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Puoi copiare il dettaglio per analizzarlo.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    TextButton(
                        onClick = {
                            val fullError = errorPopupText ?: "Errore sconosciuto"
                            clipboardManager.setText(AnnotatedString(fullError))
                        }
                    ) {
                        Text("Copia")
                    }

                    TextButton(
                        onClick = {
                            showErrorPopup = false
                            vm.clearError()
                        }
                    ) {
                        Text("Chiudi")
                    }
                }
            }
        )
    }

    // Date picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        data = picked.format(formatter)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annulla") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Delete confirm
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Button(
                            onClick = {
                                val cid = categoriaId ?: return@Button
                                vm.saveSpesa(
                                    editingId = if (isExisting) editingId else null,
                                    data = data,
                                    importo = importoValue ?: 0.0,
                                    tipo = tipo,
                                    metodoPagamento = metodo,
                                    descrizione = note.trim().ifBlank { null },
                                    categoriaId = cid,
                                    sottocategoriaId = sottocategoriaId
                                )
                                // IMPORTANTISSIMO: NON fare onBack() qui
                            },
                            enabled = canSave,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Salvataggio…")
                            } else {
                                Text("Salva")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                data = initialData
                                importoText = initialImporto
                                tipo = initialTipo
                                metodo = initialMetodo
                                note = initialNote
                                categoriaId = initialCategoriaId
                                sottocategoriaId = initialSottocategoriaId
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

        val tipoSafe = tipo.ifBlank { "uscita" }
        val importoHeader = importoValue ?: (editingSpesa?.importo ?: 0.0)
        val isEntrata = tipoSafe.equals("entrata", ignoreCase = true)

        // Header: container + testo ad alto contrasto
        val headerContainer = if (isEntrata) IncomeContainer else ExpenseContainer
        val headerOn = if (isEntrata) OnIncome else OnExpense

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (saving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // HEADER CARD (testo più chiaro e contrastato)
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
                            label = { Text(if (isEntrata) "Entrata" else "Uscita") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { },
                            label = { Text(metodo.ifBlank { "Metodo n/d" }) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        AssistChip(
                            onClick = { },
                            label = { Text(data.ifBlank { "Data n/d" }) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            // FORM CARD
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedTextField(
                        value = data,
                        onValueChange = { },
                        readOnly = true,
                        enabled = editEnabled && !saving,
                        label = { Text("Data") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { if (editEnabled && !saving) showDatePicker = true },
                                enabled = editEnabled && !saving
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = "Cambia data")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = importoText,
                        onValueChange = { if (editEnabled && !saving) importoText = it },
                        label = { Text("Importo (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = !editEnabled || saving,
                        enabled = editEnabled && !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    DropdownFieldString(
                        label = "Tipo",
                        value = tipo,
                        options = tipi,
                        enabled = editEnabled && !saving,
                        onPick = { tipo = it }
                    )

                    val categoriaOptions = remember(categorie) { categorie.map(catLabel) }
                    DropdownFieldString(
                        label = "Categoria",
                        value = categorie.firstOrNull { catId(it) == categoriaId }?.let(catLabel) ?: "Seleziona…",
                        options = categoriaOptions,
                        enabled = editEnabled && !saving && categoriaOptions.isNotEmpty() && !state.loadingLookups,
                        onPick = { pickedLabel ->
                            val found = categorie.firstOrNull { catLabel(it) == pickedLabel }
                            categoriaId = found?.let(catId)
                            sottocategoriaId = null
                            categoriaId?.let { vm.loadSottocategorie(it) }
                        }
                    )

                    val subOptions = sottocategorie.map(subLabel)
                    DropdownFieldString(
                        label = "Sottocategoria (opz.)",
                        value = sottocategorie.firstOrNull { subId(it) == sottocategoriaId }?.let(subLabel) ?: "Nessuna",
                        options = listOf("Nessuna") + subOptions,
                        enabled = editEnabled && !saving && categoriaId != null && !state.loadingLookups,
                        onPick = { pickedLabel ->
                            sottocategoriaId =
                                if (pickedLabel == "Nessuna") null
                                else sottocategorie.firstOrNull { subLabel(it) == pickedLabel }?.let(subId)
                        }
                    )

                    DropdownFieldString(
                        label = "Metodo pagamento",
                        value = metodo,
                        options = metodi,
                        enabled = editEnabled && !saving,
                        onPick = { metodo = it }
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (editEnabled && !saving) note = it },
                        label = { Text("Note (opzionale)") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = !editEnabled || saving,
                        enabled = editEnabled && !saving,
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    when {
                        state.loadingLookups -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        state.error != null -> Text(
                            "Errore: ${state.error}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Delete: solo quando editEnabled e non saving
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
    value: String?,
    options: List<String?>,
    enabled: Boolean = true,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val safeValue = value.orEmpty().ifBlank { "Seleziona…" }
    val safeOptions = remember(options) {
        options.map { it.orEmpty().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = safeValue,
            onValueChange = { },
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            safeOptions.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onPick(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}