/**
 * HomeScreen.kt
 *
 * Schermata principale dell'app: lista completa dei movimenti finanziari dell'utente.
 * Funzionalità principali:
 * - Ricerca full-text su descrizione, categoria e sottocategoria
 * - Filtri per tipo, categoria, conto e periodo temporale
 * - Ordinamento per data, importo e descrizione
 * - Swipe-to-delete e tap per modifica
 * - Pull-to-refresh per la sincronizzazione manuale
 */
package com.emanuele.gestionespese.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.ui.theme.ExpenseContainer
import com.emanuele.gestionespese.ui.theme.IncomeContainer
import com.emanuele.gestionespese.ui.theme.OnExpense
import com.emanuele.gestionespese.ui.theme.OnIncome
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.viewmodel.FilterKey
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: SpeseViewModel,
    onAdd: () -> Unit,
    onSummary: () -> Unit,
    onEdit: (Int) -> Unit,
    onDrafts: () -> Unit
) {
    val state by vm.state.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refreshIfNeeded() }

    val f = state.filters
    val filtered = remember(state.spese, f) {
        val q = f.query.trim().lowercase(Locale.getDefault())
        state.spese.filter { s ->
            val categoriaText = listOfNotNull(s.categoria, s.sottocategoria)
                .joinToString(" ").lowercase(Locale.getDefault())
            val metodoSafe = s.conto.orEmpty()
            val tipoSafe   = s.tipo.orEmpty()
            val dataSafe   = s.data.orEmpty()
            val noteSafe   = s.descrizione.orEmpty()

            val okQuery = q.isEmpty() || (
                    categoriaText.contains(q) ||
                            metodoSafe.lowercase().contains(q) ||
                            tipoSafe.lowercase().contains(q) ||
                            dataSafe.contains(q) ||
                            noteSafe.lowercase().contains(q) ||
                            s.importo.toString().contains(q)
                    )
            val okMese   = f.mese?.let   { dataSafe.startsWith(it) } ?: true
            val okTipo   = f.tipo?.let   { it == tipoSafe }          ?: true
            val okMetodo = f.metodo?.let { it == metodoSafe }        ?: true
            okQuery && okMese && okTipo && okMetodo
        }.sortedByDescending { s ->
            val data = s.data ?: ""
            try {
                if (data.contains("/"))
                    java.time.LocalDate.parse(data, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                else
                    java.time.LocalDate.parse(data, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) { java.time.LocalDate.MIN }
        }
    }

    val listState     = rememberLazyListState()
    var scrollDirDown by remember { mutableStateOf(true) }
    val isScrolling   by remember { derivedStateOf { listState.isScrollInProgress } }
    var lastIndex     by remember { mutableStateOf(0) }
    var lastOffset    by remember { mutableStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val index  = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        scrollDirDown = (index > lastIndex) || (index == lastIndex && offset > lastOffset)
        lastIndex  = index
        lastOffset = offset
    }

    val isAtTop    = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    val isAtBottom = !listState.canScrollForward
    val hasFilters = f.mese != null || f.tipo != null || f.metodo != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Movimenti", style = MaterialTheme.typography.titleLarge)
                        if (state.loading) {
                            Text(
                                "Aggiornamento…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "${filtered.size} movimenti",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Icona filtri con badge se attivi
                    BadgedBox(
                        badge = {
                            if (hasFilters) Badge()
                        }
                    ) {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filtri",
                                tint = if (hasFilters) Brand
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // FAB scroll su/giù
                AnimatedVisibility(
                    visible = isScrolling &&
                            !(scrollDirDown && isAtBottom) &&
                            !(!scrollDirDown && isAtTop),
                    enter = fadeIn(),
                    exit  = fadeOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (filtered.isEmpty()) return@launch
                                listState.animateScrollToItem(
                                    if (scrollDirDown) filtered.size - 1 else 0
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            if (scrollDirDown) Icons.Default.KeyboardArrowDown
                            else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (scrollDirDown) "Vai in fondo" else "Vai in cima"
                        )
                    }
                }

                // FAB principale aggiungi
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = Brand
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Aggiungi",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    ) { padding ->

        val mesi = remember(state.spese) {
            state.spese
                .mapNotNull { it.data?.takeIf { d -> d.length >= 7 }?.substring(0, 7) }
                .distinct().sortedDescending()
        }
        val metodi = remember(state.spese) {
            state.spese
                .mapNotNull { it.conto?.takeIf { m -> m.isNotBlank() } }
                .distinct().sorted()
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Barra ricerca
            OutlinedTextField(
                value = f.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cerca") },
                placeholder = { Text("Categoria, conto, data, note…") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor      = Brand,
                    focusedLabelColor       = Brand
                )
            )

            // Chip filtri attivi
            if (hasFilters || f.query.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    f.mese?.let { ym ->
                        InputChip(
                            selected = true,
                            onClick = { },
                            label = { Text(ym) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Rimuovi",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(0.dp)
                                )
                            },
                            modifier = Modifier.wrapContentWidth()
                        )
                        // Click sul chip stesso per rimuovere
                        // (usiamo un wrapper clickable sotto)
                    }
                    f.tipo?.let { t ->
                        InputChip(
                            selected = true,
                            onClick = { vm.clearFilter(FilterKey.TIPO) },
                            label = { Text(t.replaceFirstChar { it.uppercase() }) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi",
                                    modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    f.metodo?.let { m ->
                        InputChip(
                            selected = true,
                            onClick = { vm.clearFilter(FilterKey.METODO) },
                            label = { Text(m) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi",
                                    modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    if (hasFilters) {
                        TextButton(
                            onClick = { vm.resetFilters() },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                "Pulisci tutto",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Indicatore loading
            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Brand
                )
            }

            // Errore
            state.error?.let { err ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "Errore: $err",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Lista vuota
            if (!state.loading && state.error == null && filtered.isEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Nessun movimento trovato",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (hasFilters || f.query.isNotBlank())
                                "Prova a cambiare i filtri o la ricerca."
                            else
                                "Aggiungi il tuo primo movimento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onAdd,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand)
                        ) { Text("Aggiungi movimento") }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh    = { vm.pullRefresh() },
                    modifier     = Modifier.fillMaxSize()
                ) {
                    // ← FIX: padding bottom = altezza bottom bar (80) + FAB (56) + gap (16) = 152
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 152.dp)
                    ) {
                        items(items = filtered, key = { it.id }) { spesa ->
                            SpesaCard(
                                spesa      = spesa,
                                isDeleting = state.deletingId == spesa.id,
                                onClick    = { if (state.deletingId == null) onEdit(spesa.id) },
                                onDelete   = { vm.delete(spesa.id) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom sheet filtri
        if (showFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Filtri", style = MaterialTheme.typography.titleLarge)
                        if (hasFilters) {
                            TextButton(onClick = { vm.resetFilters() }) {
                                Text("Reset", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // Mese
                    Text(
                        "Mese",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = f.mese == null,
                            onClick  = { vm.setMese(null) },
                            label    = { Text("Tutti") }
                        )
                        mesi.forEach { ym ->
                            FilterChip(
                                selected = f.mese == ym,
                                onClick  = { vm.setMese(ym) },
                                label    = { Text(ym) }
                            )
                        }
                    }

                    // Tipo
                    Text(
                        "Tipo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = f.tipo == null,
                            onClick  = { vm.setTipo(null) },
                            label    = { Text("Tutti") }
                        )
                        FilterChip(
                            selected = f.tipo == "uscita",
                            onClick  = { vm.setTipo("uscita") },
                            label    = { Text("Uscite") }
                        )
                        FilterChip(
                            selected = f.tipo == "entrata",
                            onClick  = { vm.setTipo("entrata") },
                            label    = { Text("Entrate") }
                        )
                    }

                    // Metodo
                    Text(
                        "Conto",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = f.metodo == null,
                            onClick  = { vm.setMetodo(null) },
                            label    = { Text("Tutti") }
                        )
                        metodi.forEach { m ->
                            FilterChip(
                                selected = f.metodo == m,
                                onClick  = { vm.setMetodo(m) },
                                label    = { Text(m) }
                            )
                        }
                    }

                    Button(
                        onClick = { showFilters = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand)
                    ) { Text("Applica") }
                }
            }
        }
    }
}

@Composable
private fun SpesaCard(spesa: SpesaView, isDeleting: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val tipoSafe   = spesa.tipo.orEmpty().ifBlank { "n/d" }
    val metodoSafe = spesa.conto.orEmpty().ifBlank { "n/d" }
    val dataSafe   = spesa.data.orEmpty().ifBlank { "n/d" }
    val isEntrata  = tipoSafe.contains("entrata", ignoreCase = true)

    val categoriaLabel = remember(spesa.categoria, spesa.sottocategoria) {
        listOfNotNull(spesa.categoria, spesa.sottocategoria)
            .joinToString(" • ").ifBlank { "Senza categoria" }
    }

    val container   = if (isEntrata) IncomeContainer else ExpenseContainer
    val onContainer = if (isEntrata) OnIncome else OnExpense

    ElevatedCard(
        onClick = onClick,
        enabled = !isDeleting,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // Riga principale: categoria + importo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        categoriaLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dataSafe,
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.7f)
                    )
                }
                Text(
                    String.format(Locale.getDefault(), "%.2f €", spesa.importo),
                    style = MaterialTheme.typography.titleLarge,
                    color = onContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            // Riga chip tipo + conto
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text(tipoSafe, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.height(28.dp)
                )
                AssistChip(
                    onClick = { },
                    label = { Text(metodoSafe, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.height(28.dp)
                )
                Spacer(Modifier.weight(1f))
                // Tasto elimina con spinner durante l'eliminazione
                if (isDeleting) {
                    Box(
                        modifier = Modifier.height(28.dp).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            "Elimina",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Note se presenti
            spesa.descrizione?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = onContainer.copy(alpha = 0.15f))
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}