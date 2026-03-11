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

    LaunchedEffect(Unit) { vm.refreshIfNeeded() }

    val f = state.filters
    val filtered = remember(state.spese, f) {
        val q = f.query.trim().lowercase(Locale.getDefault())
        state.spese.filter { s ->
            val categoriaText = listOfNotNull(s.categoria, s.sottocategoria)
                .joinToString(" ").lowercase(Locale.getDefault())
            val metodoSafe = s.conto.orEmpty()
            val tipoSafe = s.tipo.orEmpty()
            val dataSafe = s.data.orEmpty()
            val noteSafe = s.descrizione.orEmpty()

            val okQuery = q.isEmpty() || (
                    categoriaText.contains(q) ||
                            metodoSafe.lowercase(Locale.getDefault()).contains(q) ||
                            tipoSafe.lowercase(Locale.getDefault()).contains(q) ||
                            dataSafe.contains(q) ||
                            noteSafe.lowercase(Locale.getDefault()).contains(q) ||
                            s.importo.toString().contains(q)
                    )
            val okMese = f.mese?.let { dataSafe.startsWith(it) } ?: true
            val okTipo = f.tipo?.let { it == tipoSafe } ?: true
            val okMetodo = f.metodo?.let { it == metodoSafe } ?: true

            okQuery && okMese && okTipo && okMetodo
        }
    }

    val listState = rememberLazyListState()
    var scrollDirDown by remember { mutableStateOf(true) }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    var lastIndex by remember { mutableStateOf(0) }
    var lastOffset by remember { mutableStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        scrollDirDown = (index > lastIndex) || (index == lastIndex && offset > lastOffset)
        lastIndex = index
        lastOffset = offset
    }

    val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    val isAtBottom = !listState.canScrollForward

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Movimenti") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = isScrolling && !(scrollDirDown && isAtBottom) && !(!scrollDirDown && isAtTop),
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    FloatingActionButton(onClick = {
                        scope.launch {
                            if (filtered.isEmpty()) return@launch
                            listState.animateScrollToItem(if (scrollDirDown) filtered.size - 1 else 0)
                        }
                    }) {
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
                .distinct().sortedDescending()
        }
        val metodi = remember(state.spese) {
            state.spese.mapNotNull { it.conto?.takeIf { m -> m.isNotBlank() } }.distinct().sorted()
        }

        Column(
            modifier = Modifier
                .padding(padding).fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = f.query, onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Cerca") },
                placeholder = { Text("Categoria, metodo, data, descrizione…") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                f.mese?.let { ym ->
                    InputChip(selected = true, onClick = { }, label = { Text(ym) },
                        trailingIcon = {
                            IconButton(onClick = { vm.clearFilter(FilterKey.MESE) }) {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi mese")
                            }
                        })
                }
                f.tipo?.let { t ->
                    InputChip(selected = true, onClick = { }, label = { Text(t.replaceFirstChar { it.uppercase() }) },
                        trailingIcon = {
                            IconButton(onClick = { vm.clearFilter(FilterKey.TIPO) }) {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi tipo")
                            }
                        })
                }
                f.metodo?.let { m ->
                    InputChip(selected = true, onClick = { }, label = { Text(m) },
                        trailingIcon = {
                            IconButton(onClick = { vm.clearFilter(FilterKey.METODO) }) {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi metodo")
                            }
                        })
                }

                AssistChip(onClick = { showFilters = true }, label = { Text("Filtri") })

                if (f.mese != null || f.tipo != null || f.metodo != null) {
                    TextButton(onClick = { vm.resetFilters() }) { Text("Pulisci") }
                }
            }

            when {
                state.loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.error != null -> Text("Errore: ${state.error}", color = MaterialTheme.colorScheme.error)
            }

            Text(
                "${filtered.size} risultati",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!state.loading && state.error == null && filtered.isEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Nessuna spesa trovata", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Prova a cambiare filtri o cerca un altro testo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Aggiungi una spesa") }
                    }
                }
            } else {
                LazyColumn(
                    state = listState, modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(items = filtered, key = { it.id }) { spesa ->
                        SpesaCard(spesa = spesa, onClick = { onEdit(spesa.id) }, onDelete = { vm.delete(spesa.id) })
                    }
                }
            }
        }

        if (showFilters) {
            ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = sheetState) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Filtri", style = MaterialTheme.typography.titleLarge)

                    Text("Mese", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(onClick = { vm.setMese(null) }, label = { Text("Tutti") })
                        mesi.forEach { ym ->
                            FilterChip(selected = f.mese == ym, onClick = { vm.setMese(ym) }, label = { Text(ym) })
                        }
                    }

                    Text("Tipo", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = f.tipo == null, onClick = { vm.setTipo(null) }, label = { Text("Tutti") })
                        FilterChip(selected = f.tipo == "uscita", onClick = { vm.setTipo("uscita") }, label = { Text("Uscite") })
                        FilterChip(selected = f.tipo == "entrata", onClick = { vm.setTipo("entrata") }, label = { Text("Entrate") })
                    }

                    Text("Metodo", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(onClick = { vm.setMetodo(null) }, label = { Text("Tutti") })
                        metodi.forEach { m ->
                            FilterChip(selected = f.metodo == m, onClick = { vm.setMetodo(m) }, label = { Text(m) })
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { vm.resetFilters() }) { Text("Reset") }
                        Button(onClick = { showFilters = false }) { Text("Applica") }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SpesaCard(spesa: SpesaView, onClick: () -> Unit, onDelete: () -> Unit) {
    val tipoSafe = spesa.tipo.orEmpty().ifBlank { "n/d" }
    val metodoSafe = spesa.conto.orEmpty().ifBlank { "n/d" }
    val dataSafe = spesa.data.orEmpty().ifBlank { "n/d" }

    // Coerente con il resto dell'app: "entrata" = reddito
    val isEntrata = tipoSafe.contains("entrata", ignoreCase = true)

    val categoriaLabel = remember(spesa.categoria, spesa.sottocategoria) {
        listOfNotNull(spesa.categoria, spesa.sottocategoria).joinToString(" • ").ifBlank { "Senza categoria" }
    }

    val container = if (isEntrata) IncomeContainer else ExpenseContainer
    val onContainer = if (isEntrata) OnIncome else OnExpense

    ElevatedCard(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
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
                    Text(categoriaLabel, style = MaterialTheme.typography.titleMedium, color = onContainer)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { }, label = { Text(tipoSafe) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface))
                        AssistChip(onClick = { }, label = { Text(metodoSafe) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        String.format(Locale.getDefault(), "%.2f €", spesa.importo),
                        style = MaterialTheme.typography.headlineSmall, color = onContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(dataSafe, style = MaterialTheme.typography.labelMedium, color = onContainer.copy(alpha = 0.80f))
                }
            }

            spesa.descrizione?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.92f))
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}