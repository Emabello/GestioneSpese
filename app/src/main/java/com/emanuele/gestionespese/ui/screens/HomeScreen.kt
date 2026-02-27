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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.ui.theme.ExpenseContainer
import com.emanuele.gestionespese.ui.theme.IncomeContainer
import com.emanuele.gestionespese.ui.theme.OnExpense
import com.emanuele.gestionespese.ui.theme.OnIncome
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

    LaunchedEffect(Unit) { vm.refresh() }

    val f = state.filters
    val filtered = remember(state.spese, f) {
        val q = f.query.trim().lowercase(Locale.getDefault())

        state.spese.filter { s ->
            val categoriaText = listOfNotNull(s.categoria, s.sottocategoria)
                .joinToString(" ")
                .lowercase(Locale.getDefault())

            val metodoSafe = s.metodoPagamento.orEmpty()
            val tipoSafe = s.tipo.orEmpty()
            val dataSafe = s.data.orEmpty()
            val noteSafe = s.descrizione.orEmpty()

            val okQuery =
                if (q.isEmpty()) true
                else (
                        categoriaText.contains(q) ||
                                metodoSafe.lowercase(Locale.getDefault()).contains(q) ||
                                tipoSafe.lowercase(Locale.getDefault()).contains(q) ||
                                dataSafe.contains(q) ||
                                noteSafe.lowercase(Locale.getDefault()).contains(q) ||
                                s.importo.toString().contains(q)
                        )

            val okMese = f.mese?.let { ym -> dataSafe.startsWith(ym) } ?: true
            val okTipo = f.tipo?.let { it == tipoSafe } ?: true
            val okMetodo = f.metodo?.let { it == metodoSafe } ?: true

            okQuery && okMese && okTipo && okMetodo
        }
    }

    // Stato lista per "salta su/giù"
    val listState = rememberLazyListState()

    // FAB sinistro: visibile solo quando scrolli + direzione
    var showJumpFab by remember { mutableStateOf(false) }
    var scrollDirDown by remember { mutableStateOf(true) } // true=↓, false=↑

    // Mostra il FAB solo durante lo scroll e per ~700ms dopo l’ultimo movimento
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }

    var lastIndex by remember { mutableStateOf(0) }
    var lastOffset by remember { mutableStateOf(0) }
    //var scrollDirDown by remember { mutableStateOf(true) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset

        scrollDirDown =
            (index > lastIndex) || (index == lastIndex && offset > lastOffset)

        lastIndex = index
        lastOffset = offset
    }

    val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    val isAtBottom = !listState.canScrollForward

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Spese") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onSummary) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Riepilogo")
                    }

                    IconButton(onClick = onDrafts) {   // 👈 NUOVO
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Draft")
                    }

                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
                    }
                }
            )
        },
        floatingActionButton = {
            // Due FAB: sinistro (salta su/giù) + destro (add)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = isScrolling &&
                            !(scrollDirDown && isAtBottom) &&
                            !(!scrollDirDown && isAtTop),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (filtered.isEmpty()) return@launch
                                val target = if (scrollDirDown) (filtered.size - 1) else 0
                                listState.animateScrollToItem(target)
                            }
                        }
                    ) {
                        Icon(
                            if (scrollDirDown) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (scrollDirDown) "Vai in fondo" else "Vai in cima"
                        )
                    }
                }

                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi")
                }
            }
        }
    ) { padding ->

        val mesi = remember(state.spese) {
            state.spese
                .mapNotNull { it.data?.takeIf { d -> d.length >= 7 }?.substring(0, 7) }
                .distinct()
                .sortedDescending()
        }

        val metodi = remember(state.spese) {
            state.spese
                .mapNotNull { it.metodoPagamento?.takeIf { m -> m.isNotBlank() } }
                .distinct()
                .sorted()
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            OutlinedTextField(
                value = f.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cerca") },
                placeholder = { Text("Categoria, metodo, data, descrizione…") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chip attivo: Mese
                f.mese?.let { ym ->
                    InputChip(
                        selected = true,
                        onClick = { },
                        label = { Text(ym) },
                        trailingIcon = {
                            IconButton(onClick = { vm.clearFilter(FilterKey.MESE) }) {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi mese")
                            }
                        }
                    )
                }

                // Chip attivo: Tipo
                f.tipo?.let { t ->
                    InputChip(
                        selected = true,
                        onClick = { },
                        label = { Text(t.replaceFirstChar { it.uppercase() }) },
                        trailingIcon = {
                            IconButton(onClick = { vm.clearFilter(FilterKey.TIPO) }) {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi tipo")
                            }
                        }
                    )
                }

                // Chip attivo: Metodo
                f.metodo?.let { m ->
                    InputChip(
                        selected = true,
                        onClick = { },
                        label = { Text(m) },
                        trailingIcon = {
                            IconButton(onClick = { vm.clearFilter(FilterKey.METODO) }) {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi metodo")
                            }
                        }
                    )
                }

                val anyActive = f.mese != null || f.tipo != null || f.metodo != null
                AssistChip(
                    onClick = { showFilters = true },
                    label = { Text("Filtri") }
                )

                if (anyActive) {
                    TextButton(onClick = {
                        vm.setMese(null); vm.setTipo(null); vm.setMetodo(null)
                    }) { Text("Pulisci") }
                }
            }

            when {
                state.loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.error != null -> Text(
                    text = "Errore: ${state.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "${filtered.size} risultati",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!state.loading && state.error == null && filtered.isEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Nessuna spesa trovata", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Prova a cambiare filtri o cerca un altro testo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                            Text("Aggiungi una spesa")
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(items = filtered, key = { it.id }) { spesa ->
                        SpesaCard(
                            spesa = spesa,
                            onClick = { onEdit(spesa.id) },
                            onDelete = { vm.delete(spesa.id) }
                        )
                    }
                }
            }
        }

        if (showFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Filtri", style = MaterialTheme.typography.titleLarge)

                    // --- Mese ---
                    Text("Mese", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { vm.setMese(null) },
                            label = { Text("Tutti") }
                        )
                        mesi.forEach { ym ->
                            FilterChip(
                                selected = f.mese == ym,
                                onClick = { vm.setMese(ym) },
                                label = { Text(ym) }
                            )
                        }
                    }

                    // --- Tipo ---
                    Text("Tipo", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = f.tipo == null,
                            onClick = { vm.setTipo(null) },
                            label = { Text("Tutti") }
                        )
                        FilterChip(
                            selected = f.tipo == "uscita",
                            onClick = { vm.setTipo("uscita") },
                            label = { Text("Uscite") }
                        )
                        FilterChip(
                            selected = f.tipo == "entrata",
                            onClick = { vm.setTipo("entrata") },
                            label = { Text("Entrate") }
                        )
                    }

                    // --- Metodo ---
                    Text("Metodo", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { vm.setMetodo(null) },
                            label = { Text("Tutti") }
                        )
                        metodi.forEach { m ->
                            FilterChip(
                                selected = f.metodo == m,
                                onClick = { vm.setMetodo(m) },
                                label = { Text(m) }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            vm.setMese(null); vm.setTipo(null); vm.setMetodo(null)
                        }) { Text("Reset") }

                        Button(onClick = { showFilters = false }) {
                            Text("Applica")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SpesaCard(
    spesa: SpesaView,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val tipoSafe = spesa.tipo.orEmpty()
    val metodoSafe = spesa.metodoPagamento.orEmpty().ifBlank { "Metodo n/d" }
    val dataSafe = spesa.data.orEmpty().ifBlank { "Data n/d" }
    val isEntrata = tipoSafe.equals("entrata", ignoreCase = true)

    val categoriaLabel = remember(spesa.categoria, spesa.sottocategoria) {
        listOfNotNull(spesa.categoria, spesa.sottocategoria)
            .joinToString(" • ")
            .ifBlank { "Senza categoria" }
    }

    val container = if (isEntrata) IncomeContainer else ExpenseContainer
    val onContainer = if (isEntrata) OnIncome else OnExpense

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = categoriaLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer
                    )
                    Spacer(Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { },
                            label = { Text(if (isEntrata) "Entrata" else "Uscita") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        AssistChip(
                            onClick = { },
                            label = { Text(metodoSafe) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.2f €", spesa.importo),
                        style = MaterialTheme.typography.headlineSmall,
                        color = onContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dataSafe,
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer.copy(alpha = 0.80f)
                    )
                }
            }

            spesa.descrizione?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.92f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}