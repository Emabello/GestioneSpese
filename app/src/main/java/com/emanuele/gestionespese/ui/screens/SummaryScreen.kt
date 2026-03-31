/**
 * SummaryScreen.kt
 *
 * Schermata di riepilogo con la dashboard personalizzabile a widget.
 * Visualizza i widget configurati dall'utente in una griglia a 2 colonne,
 * ognuno calcolato sul periodo selezionato (mese corrente, ultimi 30 giorni, anno).
 *
 * Funzionalità:
 * - Card conti ridisegnate con saldo, trend, icone e click al dettaglio
 * - Drag & drop per riordinare i widget (long-press handle in edit mode)
 * - Bottone "⚙" per configurare ogni widget (periodo, topN, contoFilter)
 * - Tasto "Modifica" nella TopAppBar per entrare in edit mode
 */
package com.emanuele.gestionespese.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import com.emanuele.gestionespese.data.model.*
import com.emanuele.gestionespese.ui.components.widgets.*
import com.emanuele.gestionespese.ui.components.widgets.saldoPerConto
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.utils.InitialBalanceManager
import com.emanuele.gestionespese.ui.theme.Danger
import com.emanuele.gestionespese.ui.theme.ExpenseContainer
import com.emanuele.gestionespese.ui.theme.IncomeContainer
import com.emanuele.gestionespese.ui.viewmodel.DashboardViewModel
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Drag & Drop nativo senza librerie esterne ─────────────────────────────
@Stable
private class DragDropState(
    val items: SnapshotStateList<WidgetConfig>,
    val onSave: (List<WidgetConfig>) -> Unit
) {
    var draggedIndex by mutableStateOf<Int?>(null)
    var dragOffsetY  by mutableFloatStateOf(0f)
    // Altezza per-indice: ogni widget può avere altezza diversa
    private val itemHeightsPx = mutableMapOf<Int, Float>()

    fun setItemHeight(index: Int, px: Float) { if (px > 0f) itemHeightsPx[index] = px }

    private fun getThreshold(idx: Int): Float =
        (itemHeightsPx[idx] ?: 160f) * 0.5f

    fun onDragStart(index: Int) {
        draggedIndex = index
        dragOffsetY  = 0f
    }

    fun onDrag(deltaY: Float) {
        val idx = draggedIndex ?: return
        dragOffsetY += deltaY
        val threshold = getThreshold(idx)
        when {
            dragOffsetY > threshold && idx < items.size - 1 -> {
                items.add(idx + 1, items.removeAt(idx))
                draggedIndex = idx + 1
                dragOffsetY -= (itemHeightsPx[idx] ?: threshold * 2)
            }
            dragOffsetY < -threshold && idx > 0 -> {
                items.add(idx - 1, items.removeAt(idx))
                draggedIndex = idx - 1
                dragOffsetY += (itemHeightsPx[idx] ?: threshold * 2)
            }
        }
    }

    fun onDragEnd() {
        onSave(items.toList())
        draggedIndex = null
        dragOffsetY  = 0f
    }
}

/** Raggruppa i widget in righe: ogni riga ha somma colSpan ≤ 6 (flow layout). */
private fun flowRows(widgets: List<WidgetConfig>): List<List<WidgetConfig>> {
    val rows = mutableListOf<MutableList<WidgetConfig>>()
    var currentRow = mutableListOf<WidgetConfig>()
    var currentSum = 0
    for (w in widgets) {
        if (currentSum + w.colSpan > 6) {
            if (currentRow.isNotEmpty()) rows.add(currentRow)
            currentRow = mutableListOf(w)
            currentSum = w.colSpan
        } else {
            currentRow.add(w)
            currentSum += w.colSpan
        }
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow)
    return rows
}

/** Spazio massimo disponibile per il widget nella sua riga (rispetta overflow e minColSpan). */
private fun maxColSpanForWidget(widgetId: String, widgets: List<WidgetConfig>): Int {
    val rows = flowRows(widgets)
    val row = rows.find { r -> r.any { it.id == widgetId } } ?: return 6
    val widget = row.first { it.id == widgetId }
    val usedByOthers = row.filter { it.id != widgetId }.sumOf { it.colSpan }
    return (6 - usedByOthers).coerceAtLeast(widget.type.minColSpan())
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    vm: SpeseViewModel,
    dashVm: DashboardViewModel,
    onBack: () -> Unit,
    onEditDashboard: () -> Unit,
    onContoDetail: (String) -> Unit = {}
) {
    val state     by vm.state.collectAsState()
    val dashState by dashVm.state.collectAsState()

    var editMode     by remember { mutableStateOf(false) }
    var showAddPopup by remember { mutableStateOf(false) }

    // Widget da configurare
    var configuringWidget by remember { mutableStateOf<WidgetConfig?>(null) }

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

    val sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    // SALDO_CONTO può essere aggiunto più volte (uno per conto)
                    val alreadyAdded = if (type == WidgetType.SALDO_CONTO) false
                                       else dashState.widgets.any { it.type == type }
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
                                        type       = type,
                                        colSpan    = type.defaultColSpan(),
                                        heightStep = type.defaultHeightStep(),
                                        position   = dashState.widgets.size
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = type.icon(),
                                    contentDescription = null,
                                    tint = if (alreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant else Brand,
                                    modifier = Modifier.size(24.dp)
                                )
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

    // ── Sheet configurazione widget ───────────────────────────────────
    configuringWidget?.let { cfg ->
        WidgetConfigSheet(
            config      = cfg,
            conti       = state.conti,
            onDismiss   = { configuringWidget = null },
            onSave      = { updated ->
                dashVm.updateWidgetConfig(cfg.id, updated)
                configuringWidget = null
            },
            sheetState  = configSheetState
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Riepilogo", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (editMode) "Modalità modifica — trascina per riordinare" else meseLabel,
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
                    AnimatedVisibility(
                        visible = !editMode,
                        enter   = fadeIn(),
                        exit    = fadeOut()
                    ) {
                        IconButton(onClick = { editMode = true }) {
                            Icon(Icons.Default.Edit,
                                contentDescription = "Modifica dashboard",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

        // Lista dei widget per il drag & drop (flat list)
        val widgetList = remember(dashState.widgets) {
            dashState.widgets.sortedBy { it.position }.toMutableStateList()
        }
        LaunchedEffect(dashState.widgets) {
            if (!editMode) {
                widgetList.clear()
                widgetList.addAll(dashState.widgets.sortedBy { it.position })
            }
        }

        val listState    = rememberLazyListState()
        val dragDropState = remember(widgetList) {
            DragDropState(widgetList) { ordered ->
                val reindexed = ordered.mapIndexed { i, w -> w.copy(position = i) }
                dashVm.saveLayout(reindexed)
            }
        }

        // Saldi cumulativi per conto (calcolati su TUTTI i movimenti + saldo iniziale)
        val summaryContext = LocalContext.current
        val contiSaldi = remember(state.spese, state.conti) {
            state.conti.map { conto ->
                val ib = InitialBalanceManager.getBalance(summaryContext, conto)
                conto to state.spese.saldoPerConto(conto, ib)
            }
        }

        // Saldo totale patrimonio
        val patrimonioNetto = remember(contiSaldi) { contiSaldi.sumOf { it.second } }

        // Uscite/entrate mese selezionato per trend card conti
        val usciteMese  = remember(speseFiltered) {
            speseFiltered.filter { it.isUscita() && !it.isTransfer() }.sumOf { it.importo }
        }
        val entrateMese = remember(speseFiltered) {
            speseFiltered.filter { it.isEntrata() && !it.isTransfer() }.sumOf { it.importo }
        }

        val rows = remember(widgetList.toList()) {
            flowRows(widgetList)
        }

        LazyColumn(
            state               = listState,
            modifier            = Modifier.padding(padding).fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Card Patrimonio netto ─────────────────────────────────
            item(key = "patrimonio") {
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.elevatedCardColors(
                        containerColor = Brand
                    ),
                    elevation = CardDefaults.elevatedCardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Patrimonio netto",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                String.format(Locale.getDefault(), "%.2f €", patrimonioNetto),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(14.dp))
                                Text(String.format(Locale.getDefault(), "%.0f €", entrateMese),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(14.dp))
                                Text(String.format(Locale.getDefault(), "%.0f €", usciteMese),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }
                }
            }

            // ── Card conti (ridisegnate) ──────────────────────────────
            if (contiSaldi.isNotEmpty()) {
                item(key = "saldi_conti") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "I tuoi conti",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            contiSaldi.forEach { (conto, saldo) ->
                                val isPositive  = saldo >= 0
                                val contoNome   = conto.substringAfter(" - ", conto)
                                ElevatedCard(
                                    onClick   = { onContoDetail(conto) },
                                    modifier  = Modifier.width(160.dp),
                                    shape     = RoundedCornerShape(16.dp),
                                    colors    = CardDefaults.elevatedCardColors(
                                        containerColor = if (isPositive) IncomeContainer else ExpenseContainer
                                    ),
                                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalance,
                                                contentDescription = null,
                                                tint = Brand,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Dettaglio",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text  = contoNome,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                        Text(
                                            text  = String.format(Locale.getDefault(), "%.2f €", saldo),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPositive) Brand else Danger
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                                contentDescription = null,
                                                tint = if (isPositive) Brand else Danger,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (isPositive) "Positivo" else "Negativo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isPositive) Brand else Danger
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Selettore mese ────────────────────────────────────────
            item(key = "mese_selector") {
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(12.dp),
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
                        IconButton(onClick = {
                            val d = LocalDate.of(selectedYear, selectedMonth, 1).minusMonths(1)
                            selectedYear  = d.year
                            selectedMonth = d.monthValue
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Mese precedente",
                                tint = Brand)
                        }

                        TextButton(onClick = {
                            selectedYear  = today.year
                            selectedMonth = today.monthValue
                        }) {
                            Text(
                                meseLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedYear == today.year &&
                                    selectedMonth == today.monthValue)
                                    Brand
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }

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
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Mese successivo",
                                tint = if (LocalDate.of(selectedYear, selectedMonth, 1)
                                        .isBefore(today.withDayOfMonth(1)))
                                    Brand
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Widget rows ───────────────────────────────────────────
            if (widgetList.isEmpty() && !editMode) {
                item(key = "empty") {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier            = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Dashboard, null,
                                tint = Brand, modifier = Modifier.size(40.dp))
                            Text("Dashboard vuota",
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tieni premuto su un'area o premi il bottone Modifica per aggiungere widget.",
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
            } else if (editMode) {
                // Edit mode: lista flat con drag & drop nativo (un widget per riga)
                // BoxWithConstraints usato per calcolare la larghezza schermo per resize
                item(key = "edit_grid") {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val screenWidthPx = constraints.maxWidth.toFloat()
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            widgetList.forEachIndexed { index, widget ->
                                val isDragging = dragDropState.draggedIndex == index
                                val maxCols = maxColSpanForWidget(widget.id, widgetList.toList())
                                val animatedHeight by animateDpAsState(
                                    targetValue = widget.heightStep.toDp(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness    = Spring.StiffnessMedium
                                    ),
                                    label = "widgetHeight_${widget.id}"
                                )
                                EditableWidgetWrapper(
                                    config            = widget,
                                    editMode          = true,
                                    isDragging        = isDragging,
                                    maxColSpan        = maxCols,
                                    screenWidthPx     = screenWidthPx,
                                    onDragStart       = { dragDropState.onDragStart(index) },
                                    onDragDelta       = { dragDropState.onDrag(it) },
                                    onDragEnd         = { dragDropState.onDragEnd() },
                                    onDelete          = { dashVm.removeWidget(widget.id) },
                                    onColSpanChange   = { dashVm.setColSpan(widget.id, it) },
                                    onHeightStepChange = { dashVm.setHeightStep(widget.id, it) },
                                    onConfigure       = { configuringWidget = widget },
                                    modifier          = Modifier
                                        .fillMaxWidth()
                                        .height(animatedHeight)
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY    = if (isDragging) dragDropState.dragOffsetY else 0f
                                            shadowElevation = if (isDragging) 32f else 0f
                                            scaleX = if (isDragging) 1.02f else 1f
                                            scaleY = if (isDragging) 1.02f else 1f
                                        }
                                        .onSizeChanged { dragDropState.setItemHeight(index, it.height.toFloat()) }
                                ) {
                                    WidgetRenderer(
                                        config   = widget,
                                        spese    = state.spese,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // View mode: righe con colSpan peso (6 colonne)
                items(
                    items = rows,
                    key   = { row -> row.joinToString("_") { it.id } }
                ) { row ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { widget ->
                            val rowHeightDp = row.maxOf { it.heightStep.toDp().value }.dp
                            EditableWidgetWrapper(
                                config            = widget,
                                editMode          = false,
                                isDragging        = false,
                                maxColSpan        = 6,
                                screenWidthPx     = 0f,
                                onLongPress       = { editMode = true },
                                onDelete          = { },
                                onColSpanChange   = { },
                                onHeightStepChange = { },
                                onConfigure       = { },
                                modifier          = Modifier
                                    .weight(widget.colSpan.toFloat())
                                    .height(rowHeightDp)
                            ) {
                                WidgetRenderer(
                                    config   = widget,
                                    spese    = state.spese,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        // Slot vuoto se la riga non riempie 6 colonne
                        val remaining = 6 - row.sumOf { it.colSpan }
                        if (remaining > 0) {
                            Spacer(Modifier.weight(remaining.toFloat()))
                        }
                    }
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Sheet configurazione widget ────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigSheet(
    config: WidgetConfig,
    conti: List<String>,
    onDismiss: () -> Unit,
    onSave: (WidgetConfig) -> Unit,
    sheetState: SheetState
) {
    var periodo     by remember(config) { mutableStateOf(config.periodo) }
    var topN        by remember(config) { mutableIntStateOf(config.topN) }
    var contoFilter by remember(config) { mutableStateOf(config.contoFilter ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Configura: ${config.type.displayName()}",
                style = MaterialTheme.typography.titleLarge
            )

            // Periodo (tutti tranne ANDAMENTO_MENSILE che usa sempre gli ultimi 6 mesi fissi)
            if (config.type != WidgetType.ANDAMENTO_MENSILE) {
                Text("Periodo", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetPeriodo.entries.forEach { p ->
                        FilterChip(
                            selected = periodo == p,
                            onClick  = { periodo = p },
                            label    = { Text(p.label()) }
                        )
                    }
                }
            }

            // TopN (solo per TOP_CATEGORIE e ULTIMI_MOVIMENTI)
            if (config.type == WidgetType.TOP_CATEGORIE || config.type == WidgetType.ULTIMI_MOVIMENTI) {
                Text("Numero elementi: $topN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value         = topN.toFloat(),
                    onValueChange = { topN = it.toInt() },
                    valueRange    = 3f..10f,
                    steps         = 6,
                    colors        = SliderDefaults.colors(thumbColor = Brand, activeTrackColor = Brand)
                )
            }

            // Conto filter (solo per SALDO_CONTO) — mostrato sempre, con messaggio se vuoto
            if (config.type == WidgetType.SALDO_CONTO) {
                Text("Conto da mostrare", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (conti.isEmpty()) {
                    Text(
                        "Nessun conto disponibile. Sincronizza i dati e riprova.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        conti.forEach { c ->
                            val nomeC = c.substringAfter(" - ", c)
                            FilterChip(
                                selected = contoFilter == c,
                                onClick  = { contoFilter = c },
                                label    = { Text(nomeC) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = onDismiss) { Text("Annulla") }
                Button(
                    onClick = {
                        onSave(config.copy(
                            periodo     = periodo,
                            topN        = topN,
                            contoFilter = contoFilter.takeIf { it.isNotBlank() }
                        ))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand)
                ) { Text("Salva") }
            }
        }
    }
}

// ── Widget wrapper con drag handle, config, delete e resize angolo ─────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableWidgetWrapper(
    config: WidgetConfig,
    editMode: Boolean,
    isDragging: Boolean,
    maxColSpan: Int,
    screenWidthPx: Float,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDelete: () -> Unit,
    onColSpanChange: (Int) -> Unit,
    onHeightStepChange: (WidgetHeightStep) -> Unit,
    onConfigure: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.combinedClickable(
            onClick     = { },
            onLongClick = onLongPress
        )
    ) {
        content()

        // Barra superiore edit mode: drag handle + config + delete
        AnimatedVisibility(
            visible  = editMode,
            enter    = fadeIn() + scaleIn(),
            exit     = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle (≡) — long-press per riordinare
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart  = { onDragStart() },
                                onDragEnd    = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag       = { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.y)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Trascina per riordinare",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row {
                    // ⚙ Configura
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .combinedClickable(onClick = onConfigure),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape    = CircleShape,
                            color    = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxSize()
                        ) { }
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Configura",
                            tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // ✕ Elimina
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
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
            }
        }

        // Handle angolo inferiore destro: drag X=larghezza, drag Y=altezza (solo in edit mode)
        AnimatedVisibility(
            visible  = editMode,
            enter    = fadeIn() + scaleIn(),
            exit     = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            ResizeCornerHandle(
                config             = config,
                maxColSpan         = maxColSpan,
                screenWidthPx      = screenWidthPx,
                onColSpanChange    = onColSpanChange,
                onHeightStepChange = onHeightStepChange
            )
        }
    }
}

// ── Handle angolo S-E: drag X=colSpan, drag Y=heightStep ──────────────────
@Composable
private fun ResizeCornerHandle(
    config: WidgetConfig,
    maxColSpan: Int,
    screenWidthPx: Float,
    onColSpanChange: (Int) -> Unit,
    onHeightStepChange: (WidgetHeightStep) -> Unit
) {
    var accX             by remember { mutableFloatStateOf(0f) }
    var accY             by remember { mutableFloatStateOf(0f) }
    var dragStartColSpan by remember { mutableIntStateOf(config.colSpan) }
    var previewColSpan   by remember(config.colSpan) { mutableIntStateOf(config.colSpan) }
    var isOverflow       by remember { mutableStateOf(false) }
    var liveHeightStep   by remember(config.heightStep) { mutableStateOf(config.heightStep) }

    // 1 unità colonna in px; protezione divisione per zero
    val colUnitPx = if (screenWidthPx > 0f) screenWidthPx / 6f else 60f
    // Soglia di drag verticale per cambiare step (in px, ~40dp a 2dp/px)
    val heightThresholdPx = 80f

    val handleColor = if (isOverflow) Danger else Brand

    Box(
        modifier = Modifier
            .size(36.dp)  // area touch
            .pointerInput(config.id, maxColSpan) {
                detectDragGestures(
                    onDragStart = { _ ->
                        accX             = 0f
                        accY             = 0f
                        dragStartColSpan = config.colSpan
                        liveHeightStep   = config.heightStep
                    },
                    onDragEnd = {
                        onColSpanChange(previewColSpan)
                        accX       = 0f
                        accY       = 0f
                        isOverflow = false
                    },
                    onDragCancel = {
                        previewColSpan = config.colSpan
                        accX           = 0f
                        accY           = 0f
                        isOverflow     = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accX += dragAmount.x
                        accY += dragAmount.y

                        // ── Larghezza: snap a VALID_COL_SPANS ─────────────────────────────
                        val rawFloat   = dragStartColSpan + accX / colUnitPx
                        val targetCol  = VALID_COL_SPANS.minByOrNull { abs(it - rawFloat) } ?: dragStartColSpan
                        val clamped    = targetCol.coerceIn(config.type.minColSpan(), maxColSpan)
                        isOverflow     = targetCol > maxColSpan
                        previewColSpan = clamped

                        // ── Altezza: cambia step quando si supera la soglia ───────────────
                        val steps      = WidgetHeightStep.entries
                        val currentIdx = steps.indexOf(liveHeightStep)
                        when {
                            accY > heightThresholdPx && currentIdx < steps.size - 1 -> {
                                val newStep = steps[currentIdx + 1]
                                liveHeightStep = newStep
                                onHeightStepChange(newStep)
                                accY = 0f
                            }
                            accY < -heightThresholdPx && currentIdx > 0 -> {
                                val newStep = steps[currentIdx - 1]
                                liveHeightStep = newStep
                                onHeightStepChange(newStep)
                                accY = 0f
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Indicatore visivo: quadratino colorato nell'angolo
        Surface(
            shape    = RoundedCornerShape(topStart = 10.dp, topEnd = 2.dp, bottomEnd = 2.dp, bottomStart = 2.dp),
            color    = handleColor,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.OpenWith,
                contentDescription = "Ridimensiona",
                tint               = Color.White,
                modifier           = Modifier
                    .padding(4.dp)
                    .fillMaxSize()
            )
        }
    }
}

