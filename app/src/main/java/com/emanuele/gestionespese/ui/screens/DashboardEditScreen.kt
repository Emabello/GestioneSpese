/**
 * DashboardEditScreen.kt
 *
 * Schermata di modifica della dashboard personalizzata. Permette all'utente di:
 * - Aggiungere nuovi widget dal catalogo disponibile
 * - Rimuovere widget esistenti
 * - Riordinare i widget con i pulsanti Su/Giù
 * - Cambiare la dimensione (WIDE/SMALL) di ogni widget
 *
 * Le modifiche vengono salvate tramite [DashboardViewModel.saveLayout].
 */
package com.emanuele.gestionespese.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.*
import com.emanuele.gestionespese.ui.components.widgets.label
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditScreen(
    dashVm: DashboardViewModel,
    onBack: () -> Unit
) {
    val state by dashVm.state.collectAsState()
    val widgets = state.widgets.sortedBy { it.position }

    // Widget disponibili da aggiungere
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Aggiungi widget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetType.entries.forEach { type ->
                        val alreadyAdded = widgets.any { it.type == type }
                        OutlinedButton(
                            onClick = {
                                if (!alreadyAdded) {
                                    dashVm.addWidget(
                                        WidgetConfig(
                                            type     = type,
                                            size     = if (type == WidgetType.TOTALE_USCITE ||
                                                type == WidgetType.TOTALE_ENTRATE)
                                                WidgetSize.SMALL else WidgetSize.WIDE,
                                            position = widgets.size
                                        )
                                    )
                                }
                                showAddDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = !alreadyAdded
                        ) {
                            Text(type.displayName())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Chiudi") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personalizza dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi widget", tint = Brand)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Riordina o rimuovi i widget della tua dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            widgets.forEach { widget ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                widget.type.displayName(),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${widget.size.name.lowercase()} · ${widget.periodo.label()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { dashVm.moveUp(widget.id) }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Su")
                            }
                            IconButton(onClick = { dashVm.moveDown(widget.id) }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Giù")
                            }
                            IconButton(onClick = { dashVm.removeWidget(widget.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Rimuovi",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun WidgetType.displayName(): String = when (this) {
    WidgetType.TOTALE_USCITE    -> "Totale uscite"
    WidgetType.TOTALE_ENTRATE   -> "Totale entrate"
    WidgetType.SALDO_MESE       -> "Saldo mese"
    WidgetType.GRAFICO_TORTA    -> "Grafico torta"
    WidgetType.ULTIMI_MOVIMENTI -> "Ultimi movimenti"
    WidgetType.TOP_CATEGORIE    -> "Top categorie"
    WidgetType.SALDO_CONTO      -> "Saldo conto"
    WidgetType.ANDAMENTO_MENSILE -> "Andamento mensile"
}

fun WidgetType.description(): String = when (this) {
    WidgetType.TOTALE_USCITE    -> "Totale uscite del periodo selezionato"
    WidgetType.TOTALE_ENTRATE   -> "Totale entrate del periodo selezionato"
    WidgetType.SALDO_MESE       -> "Differenza entrate - uscite"
    WidgetType.GRAFICO_TORTA    -> "Distribuzione spese per categoria"
    WidgetType.ULTIMI_MOVIMENTI -> "Gli ultimi N movimenti inseriti"
    WidgetType.TOP_CATEGORIE    -> "Le categorie con più spese"
    WidgetType.SALDO_CONTO      -> "Saldo e movimenti di un conto specifico"
    WidgetType.ANDAMENTO_MENSILE -> "Trend saldo ultimi 6 mesi"
}
