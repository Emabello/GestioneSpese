/**
 * SpesaFormScreen.kt
 *
 * Form per l'aggiunta e la modifica di una spesa/movimento finanziario.
 * In modalità modifica (se `spesaId != null`), i campi vengono pre-compilati
 * con i dati della spesa esistente. Supporta anche la pre-compilazione da
 * bozza bancaria (draft prefill).
 *
 * La categoria e la sottocategoria vengono suggerite automaticamente
 * in base alle associazioni UTC dell'utente quando si seleziona il tipo.
 */
package com.emanuele.gestionespese.ui.screens

import android.content.ClipData
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.repo.SpesaDraftRepository
import com.emanuele.gestionespese.ui.theme.ExpenseContainer
import com.emanuele.gestionespese.ui.theme.IncomeContainer
import com.emanuele.gestionespese.ui.theme.OnExpense
import com.emanuele.gestionespese.ui.theme.OnIncome
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import kotlinx.coroutines.launch
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
    draftId: Long? = null,
    onBack: () -> Unit
) {
    val state   by vm.state.collectAsState()
    val context = LocalContext.current

    val draftRepo = remember(draftId) {
        if (draftId != null) {
            val db = (context.applicationContext as MyApp).db
            SpesaDraftRepository(db.spesaDraftDao())
        } else null
    }

    val editingSpesa: SpesaView? = remember(state.spese, editingId) {
        if (editingId == -1) null else state.spese.firstOrNull { it.id == editingId }
    }

    val formatter  = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val isExisting = editingId != -1
    val saving     = state.saving

    // ── Determina se c'è un prefill attivo da draft ───────────────────
    val hasDraftPrefill = editingId == -1 && state.draftPrefillTick != 0L
    android.util.Log.d("DRAFT_DEBUG", "=== FORM COMPOSTO ===")
    android.util.Log.d("DRAFT_DEBUG", "hasDraftPrefill=$hasDraftPrefill")
    android.util.Log.d("DRAFT_DEBUG", "draftPrefillTick=${state.draftPrefillTick}")
    android.util.Log.d("DRAFT_DEBUG", "draftData=${state.draftData}")
    android.util.Log.d("DRAFT_DEBUG", "draftImporto=${state.draftImporto}")
    android.util.Log.d("DRAFT_DEBUG", "draftMetodo=${state.draftMetodo}")
    android.util.Log.d("DRAFT_DEBUG", "draftDescrizione=${state.draftDescrizione}")

    // ── Variabili di stato — inizializzate subito dal prefill se disponibile ──
    var editEnabled by remember(editingId, state.draftPrefillTick) {
        mutableStateOf(editingId == -1)
    }

    var data by remember(editingSpesa, state.draftPrefillTick) {
        mutableStateOf(
            when {
                hasDraftPrefill && state.draftData != null -> state.draftData!!
                editingSpesa != null                       -> editingSpesa.data ?: LocalDate.now().format(formatter)
                else                                       -> LocalDate.now().format(formatter)
            }
        )
    }

    var importoText by remember(editingSpesa, state.draftPrefillTick) {
        mutableStateOf(
            when {
                hasDraftPrefill && state.draftImporto != null ->
                    String.format(Locale.getDefault(), "%.2f", state.draftImporto)
                editingSpesa != null ->
                    String.format(Locale.getDefault(), "%.2f", editingSpesa.importo)
                else -> ""
            }
        )
    }

    var tipo by remember(editingSpesa, state.draftPrefillTick) {
        mutableStateOf(editingSpesa?.tipo.orEmpty())
    }

    var conto by remember(editingSpesa, state.draftPrefillTick) {
        mutableStateOf(
            when {
                hasDraftPrefill && state.draftMetodo != null -> state.draftMetodo!!
                editingSpesa != null                         -> editingSpesa.conto.orEmpty()
                else                                         -> ""
            }
        )
    }

    var note by remember(editingSpesa, state.draftPrefillTick) {
        mutableStateOf(
            when {
                hasDraftPrefill && state.draftDescrizione != null -> state.draftDescrizione!!
                editingSpesa != null                              -> editingSpesa.descrizione.orEmpty()
                else                                              -> ""
            }
        )
    }

    var categoria by remember(editingSpesa, state.draftPrefillTick) {
        mutableStateOf(editingSpesa?.categoria?.trim().orEmpty())
    }

    var sottocategoria by remember(editingSpesa, state.draftPrefillTick) {
        mutableStateOf(editingSpesa?.sottocategoria?.trim().orEmpty())
    }

    // Valori iniziali per il reset "Annulla"
    val initialData           = if (hasDraftPrefill && state.draftData != null) state.draftData!!
    else editingSpesa?.data ?: LocalDate.now().format(formatter)
    val initialImporto        = if (hasDraftPrefill && state.draftImporto != null)
        String.format(Locale.getDefault(), "%.2f", state.draftImporto)
    else editingSpesa?.importo?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: ""
    val initialTipo           = editingSpesa?.tipo.orEmpty()
    val initialConto          = if (hasDraftPrefill && state.draftMetodo != null) state.draftMetodo!!
    else editingSpesa?.conto.orEmpty()
    val initialNote           = if (hasDraftPrefill && state.draftDescrizione != null) state.draftDescrizione!!
    else editingSpesa?.descrizione.orEmpty()
    val initialCategoria      = editingSpesa?.categoria?.trim().orEmpty()
    val initialSottocategoria = editingSpesa?.sottocategoria?.trim().orEmpty()

    var prevTipo      by remember { mutableStateOf<String?>(null) }
    var prevCategoria by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadLookupsIfNeeded() }

    LaunchedEffect(tipo) {
        if (prevTipo != null && prevTipo != tipo) {
            categoria      = ""
            sottocategoria = ""
        }
        prevTipo = tipo
    }

    LaunchedEffect(categoria) {
        if (prevCategoria != null && prevCategoria != categoria) {
            sottocategoria = ""
        }
        prevCategoria = categoria
    }

    var showErrorPopup by remember { mutableStateOf(false) }
    var errorPopupText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank()) { errorPopupText = err; showErrorPopup = true }
    }

    // ── Salvataggio OK — cancella draft SOLO qui ──────────────────────
    LaunchedEffect(state.saveOkTick) {
        if (state.saveOkTick != 0L) {
            draftId?.let { id -> launch { draftRepo?.delete(id) } }
            vm.consumeSaveOk()
            editEnabled = false
            onBack()
        }
    }

    var showDatePicker    by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val importoValue = importoText.replace(",", ".").toDoubleOrNull()

    val canSave = editEnabled && !saving &&
            importoValue != null && importoValue > 0.0 &&
            data.isNotBlank() && tipo.isNotBlank() &&
            conto.isNotBlank() && categoria.isNotBlank()

    val utcsUserActive        = remember(state.utcs) { state.utcs.filter { it.attivo } }
    val tipiOptions           = remember(utcsUserActive) {
        utcsUserActive.map { it.tipologia.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val categorieOptions      = remember(utcsUserActive, tipo) {
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

    // ── Dialogs ───────────────────────────────────────────────────────
    if (showErrorPopup) {
        val clipboard = LocalClipboard.current
        val scope     = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { showErrorPopup = false; vm.clearError() },
            title = { Text("Errore durante il salvataggio") },
            text  = {
                Column {
                    Text(errorPopupText ?: "Errore sconosciuto")
                    Spacer(Modifier.height(12.dp))
                    Text("Puoi copiare il dettaglio per analizzarlo.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annulla") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare questa spesa?") },
            text  = { Text("L'operazione è definitiva.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    if (isExisting) vm.delete(editingId)
                    onBack()
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") } }
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────
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
                            Icon(Icons.Default.Edit, contentDescription = "Modifica",
                                tint = if (editEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = editEnabled) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                vm.saveSpesa(
                                    editingId      = if (isExisting) editingId else null,
                                    data           = data,
                                    importo        = importoValue ?: 0.0,
                                    tipo           = tipo,
                                    conto          = conto,
                                    descrizione    = note.trim().ifBlank { null },
                                    categoria      = categoria.trim(),
                                    sottocategoria = sottocategoria.trim().ifBlank { null }
                                )
                            },
                            enabled  = canSave,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Salvataggio…")
                            } else {
                                Text("Salva", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (draftId != null) {
                                    onBack()
                                } else {
                                    data           = initialData
                                    importoText    = initialImporto
                                    tipo           = initialTipo
                                    conto          = initialConto
                                    note           = initialNote
                                    categoria      = initialCategoria
                                    sottocategoria = initialSottocategoria
                                    editEnabled    = false
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            enabled  = !saving
                        ) { Text("Annulla", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    ) { padding ->

        val isEntrata       = tipo.contains("entrata", ignoreCase = true)
        val importoHeader   = importoValue ?: (editingSpesa?.importo ?: 0.0)
        val headerContainer = if (isEntrata) IncomeContainer else ExpenseContainer
        val headerOn        = if (isEntrata) OnIncome else OnExpense

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(containerColor = headerContainer),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Importo",
                                style = MaterialTheme.typography.labelMedium,
                                color = headerOn.copy(alpha = 0.7f))
                            Text(
                                String.format(Locale.getDefault(), "%.2f €", importoHeader),
                                style = MaterialTheme.typography.headlineMedium,
                                color = headerOn
                            )
                        }
                        AssistChip(
                            onClick = { },
                            label   = { Text(tipo.ifBlank { "Tipo n/d" }) },
                            colors  = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = headerOn.copy(alpha = 0.15f))
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        AssistChip(onClick = { },
                            label  = { Text(conto.ifBlank { "Conto n/d" }) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ))
                        AssistChip(onClick = { },
                            label  = { Text(data.ifBlank { "Data n/d" }) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ))
                    }
                }
            }

            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (state.loadingLookups) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Text("Dati movimento",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value         = data,
                        onValueChange = { },
                        readOnly      = true,
                        enabled       = editEnabled && !saving,
                        label         = { Text("Data") },
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { if (editEnabled && !saving) showDatePicker = true },
                                enabled = editEnabled && !saving
                            ) { Icon(Icons.Default.DateRange, contentDescription = "Cambia data") }
                        }
                    )

                    OutlinedTextField(
                        value         = importoText,
                        onValueChange = { if (editEnabled && !saving) importoText = it },
                        label         = { Text("Importo (€)") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        readOnly      = !editEnabled || saving,
                        enabled       = editEnabled && !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors        = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text("Classificazione",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    DropdownFieldString("Tipologia", tipo, tipiOptions,
                        editEnabled && !saving && !state.loadingLookups) { tipo = it }

                    DropdownFieldString("Categoria", categoria, categorieOptions,
                        editEnabled && !saving && !state.loadingLookups) { categoria = it }

                    DropdownFieldString("Sottocategoria (opzionale)", sottocategoria,
                        sottocategorieOptions,
                        editEnabled && !saving && !state.loadingLookups && categoria.isNotBlank()
                    ) { sottocategoria = it }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text("Conto e note",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    DropdownFieldString("Conto", conto, contiOptions,
                        editEnabled && !saving && !state.loadingLookups) { conto = it }

                    OutlinedTextField(
                        value         = note,
                        onValueChange = { if (editEnabled && !saving) note = it },
                        label         = { Text("Note (opzionale)") },
                        modifier      = Modifier.fillMaxWidth(),
                        readOnly      = !editEnabled || saving,
                        enabled       = editEnabled && !saving,
                        minLines      = 2,
                        colors        = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            AnimatedVisibility(visible = editEnabled && isExisting && !saving) {
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Zona pericolosa",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick  = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                        ) { Text("Elimina spesa") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
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

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value         = value.ifBlank { "Seleziona…" },
            onValueChange = { },
            readOnly      = true,
            enabled       = enabled,
            label         = { Text(label) },
            modifier      = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            colors        = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant
            ),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text    = { Text(opt) },
                    onClick = { onPick(opt); expanded = false }
                )
            }
        }
    }
}