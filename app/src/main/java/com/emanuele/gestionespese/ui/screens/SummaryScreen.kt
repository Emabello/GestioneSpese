package com.emanuele.gestionespese.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(vm: SpeseViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()

    val mesi = remember(state.spese) {
        state.spese
            .mapNotNull { it.data }
            .filter { it.length >= 7 }
            .map { it.substring(0, 7) }
            .distinct()
            .sortedDescending()
    }

    var selectedMese by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mesi) {
        if (selectedMese == null) selectedMese = mesi.firstOrNull()
    }

    val speseMese = remember(state.spese, selectedMese) {
        val ym = selectedMese
        if (ym == null) emptyList()
        else state.spese.filter { it.data?.startsWith(ym) == true }
    }

    val (entrate, uscite) = remember(speseMese) {
        val ent = speseMese.filter { it.tipo == "entrata" }.sumOf { it.importo }
        val usc = speseMese.filter { it.tipo == "uscita" }.sumOf { it.importo }
        ent to usc
    }
    val saldo = entrate - uscite

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riepilogo") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Indietro") }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Seleziona mese", style = MaterialTheme.typography.titleMedium)

            MonthPicker(mesi = mesi, selected = selectedMese, onPick = { selectedMese = it })

            SummaryCard(title = "Saldo", value = saldo, big = true)
            SummaryCard(title = "Entrate", value = entrate)
            SummaryCard(title = "Uscite", value = uscite)

            Spacer(Modifier.height(8.dp))

            Text(
                "Movimenti: ${speseMese.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPicker(mesi: List<String>, selected: String?, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Mese") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            mesi.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m) },
                    onClick = {
                        onPick(m)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: Double, big: Boolean = false) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = String.format(Locale.getDefault(), "%.2f €", value),
                style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall
            )
        }
    }
}