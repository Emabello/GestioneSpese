/**
 * SummaryScreen.kt
 *
 * Schermata di riepilogo con la dashboard personalizzabile a widget.
 * Visualizza i widget configurati dall'utente in una griglia a 2 colonne,
 * ognuno calcolato sul periodo selezionato (mese corrente, ultimi 30 giorni, anno).
 *
 * I widget disponibili sono definiti in [WidgetConfig] e renderizzati da [WidgetRenderer].
 * La dashboard è modificabile dalla schermata [DashboardEditScreen].
 */
package com.emanuele.gestionespese.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.*
import com.emanuele.gestionespese.ui.components.widgets.*
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.viewmodel.DashboardViewModel
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Raggruppa i widget in righe: WIDE da soli, SMALL a coppie
private fun groupWidgets(widgets: List<WidgetConfig>): List<List<WidgetConfig>> {
    val rows = mutableListOf<List<WidgetConfig>>()
    var i = 0
    while (i < widgets.size) {
        val current = widgets[i]
        if (current.size == WidgetSize.WIDE) {
            rows.add(listOf(current))
            i++
        } else {
            val next = widgets.getOrNull(i + 1)
            if (next != null && next.size == WidgetSize.SMALL) {
                rows.add(listOf(current, next))
                i += 2
            } else {
                rows.add(listOf(current))
                i++
            }
        }
    }
    return rows
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    vm: SpeseViewModel,
    dashVm: DashboardViewModel,
    onBack: () -> Unit,
    onEditDashboard: () -> Unit
) {
    val state     by vm.state.collectAsState()
    val dashState by dashVm.state.collectAsState()

    var editMode     by remember { mutableStateOf(false) }
    var showAddPopup by remember { mutableStateOf(false) }

    // ── Selettore mese ────────────────────────────────────────────────
    val today = remember { LocalDate.now() }
    var selectedYear  by remember { mutableIntStateOf(today.year) }
    var selectedMonth by remember { mutableIntStateOf(today.monthValue) }

    // Filtra le spese per il mese/anno selezionato
    val speseFiltered = remember(state.spese, state.syncDone, selectedYear, selectedMonth) {
        state.spese.filter { s ->
            val data = s.data ?: return@filter false
            try {
                val d = if (data.contains("/")) {
                    LocalDate.parse(data, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                } else {
                    LocalDate.parse(data, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                }
                d.year == selectedYear && d.monthValue == selectedMonth
            } catch (e: Exception) { false }
        }
    }

    val meseLabel = remember(selectedYear, selectedMonth) {
        LocalDate.of(selectedYear, selectedMonth, 1)
            .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
            .replaceFirstChar { it.uppercase() }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Popup aggiungi widget ─────────────────────────────────────────
    if (showAddPopup) {
        ModalBottomSheet(
            onDismissRequest = { showAddPopup = false },
            sheetState       = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Aggiungi widget",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "Seleziona il widget da aggiungere",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                WidgetType.entries.forEach { type ->
                    val alreadyAdded = dashState.widgets.any { it.type == type }
                    ElevatedCard(
                        modifier  = Modifier.fillMaxWidth(),
                        colors    = CardDefaults.elevatedCardColors(
                            containerColor = if (alreadyAdded)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            if (alreadyAdded) 0.dp else 1.dp
                        ),
                        onClick   = {
                            if (!alreadyAdded) {
                                dashVm.addWidget(
                                    WidgetConfig(
                                        type     = type,
                                        size     = if (type == WidgetType.TOTALE_USCITE ||
                                            type == WidgetType.TOTALE_ENTRATE)
                                            WidgetSize.SMALL else WidgetSize.WIDE,
                                        position = dashState.widgets.size
                                    )
                                )
                                showAddPopup = false
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    type.displayName(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (alreadyAdded)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    type.description(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (alreadyAdded) {
                                Text("Già aggiunto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Brand)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Riepilogo", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (editMode) "Modalità modifica" else "Dashboard personale",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (editMode) Brand
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = editMode,
                        enter   = fadeIn() + scaleIn(),
                        exit    = fadeOut() + scaleOut()
                    ) {
                        Row {
                            IconButton(onClick = { showAddPopup = true }) {
                                Icon(Icons.Default.Add,
                                    contentDescription = "Aggiungi widget",
                                    tint = Brand)
                            }
                            TextButton(onClick = { editMode = false }) {
                                Text("Fine", color = Brand)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->

        if (dashState.isLoading || state.loading) {
            Box(
                modifier         = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Brand) }
            return@Scaffold
        }

        val rows = remember(dashState.widgets) {
            groupWidgets(dashState.widgets.sortedBy { it.position })
        }

        LazyColumn(
            modifier            = Modifier.padding(padding).fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state               = rememberLazyListState()
        ) {

            // ── Selettore mese — sempre in cima ──────────────────────
            item(key = "mese_selector") {
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(1.dp)
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // Freccia sinistra — mese precedente
                        IconButton(onClick = {
                            val d = LocalDate.of(selectedYear, selectedMonth, 1).minusMonths(1)
                            selectedYear  = d.year
                            selectedMonth = d.monthValue
                        }) {
                            Icon(Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Mese precedente",
                                tint = Brand)
                        }

                        // Label mese cliccabile → torna al mese corrente
                        TextButton(onClick = {
                            selectedYear  = today.year
                            selectedMonth = today.monthValue
                        }) {
                            Text(
                                meseLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selectedYear == today.year &&
                                    selectedMonth == today.monthValue)
                                    Brand
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Freccia destra — mese successivo (max mese corrente)
                        IconButton(
                            onClick = {
                                val d = LocalDate.of(selectedYear, selectedMonth, 1).plusMonths(1)
                                if (!d.isAfter(today.withDayOfMonth(1))) {
                                    selectedYear  = d.year
                                    selectedMonth = d.monthValue
                                }
                            },
                            enabled = LocalDate.of(selectedYear, selectedMonth, 1)
                                .isBefore(today.withDayOfMonth(1))
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight,
                                contentDescription = "Mese successivo",
                                tint = if (LocalDate.of(selectedYear, selectedMonth, 1)
                                        .isBefore(today.withDayOfMonth(1)))
                                    Brand
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Widget rows ──────────────────────────────────────────
            if (rows.isEmpty()) {
                item(key = "empty") {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier            = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Dashboard vuota",
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tieni premuto su un'area per entrare in modalità modifica e aggiungere widget.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { editMode = true; showAddPopup = true },
                                colors  = ButtonDefaults.buttonColors(containerColor = Brand)
                            ) { Text("Aggiungi widget") }
                        }
                    }
                }
            } else {
                items(items = rows, key = { row -> row.joinToString("_") { it.id } }) { row ->
                    if (row.size == 2) {
                        // Due SMALL affiancati
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { widget ->
                                EditableWidgetWrapper(
                                    config      = widget,
                                    editMode    = editMode,
                                    onLongPress = { editMode = true },
                                    onDelete    = { dashVm.removeWidget(widget.id) },
                                    onResize    = { dashVm.toggleSize(widget.id) },
                                    modifier    = Modifier.weight(1f)
                                ) {
                                    WidgetRenderer(
                                        config   = widget,
                                        spese    = speseFiltered,   // ← spese filtrate per mese
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        // WIDE singolo
                        val widget = row.first()
                        EditableWidgetWrapper(
                            config      = widget,
                            editMode    = editMode,
                            onLongPress = { editMode = true },
                            onDelete    = { dashVm.removeWidget(widget.id) },
                            onResize    = { dashVm.toggleSize(widget.id) },
                            modifier    = Modifier.fillMaxWidth()
                        ) {
                            WidgetRenderer(
                                config   = widget,
                                spese    = speseFiltered,   // ← spese filtrate per mese
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableWidgetWrapper(
    config: WidgetConfig,
    editMode: Boolean,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onResize: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.combinedClickable(
            onClick    = { },
            onLongClick = onLongPress
        )
    ) {
        content()

        // ✕ cancella — in alto a destra
        AnimatedVisibility(
            visible  = editMode,
            enter    = fadeIn() + scaleIn(),
            exit     = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxSize()
                ) { }
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Rimuovi",
                    tint     = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ↔ resize — in basso a destra
        AnimatedVisibility(
            visible  = editMode,
            enter    = fadeIn() + scaleIn(),
            exit     = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onResize),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape    = CircleShape,
                    color    = Brand,
                    modifier = Modifier.fillMaxSize()
                ) { }
                Icon(
                    Icons.Default.OpenInFull,
                    contentDescription = "Ridimensiona",
                    tint     = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}