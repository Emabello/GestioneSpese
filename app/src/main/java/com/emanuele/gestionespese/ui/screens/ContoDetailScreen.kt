/**
 * ContoDetailScreen.kt
 *
 * Schermata di dettaglio per un singolo conto corrente.
 * Mostra il saldo cumulativo (tutto il tempo) e la lista completa dei movimenti
 * associati a quel conto (sia come conto principale che come conto destinazione
 * per i trasferimenti).
 */
package com.emanuele.gestionespese.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.ui.components.widgets.isTransfer
import com.emanuele.gestionespese.ui.components.widgets.saldoPerConto
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.utils.InitialBalanceManager
import com.emanuele.gestionespese.ui.theme.Danger
import com.emanuele.gestionespese.ui.theme.ExpenseContainer
import com.emanuele.gestionespese.ui.theme.IncomeContainer
import com.emanuele.gestionespese.ui.theme.OnExpense
import com.emanuele.gestionespese.ui.theme.OnIncome
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContoDetailScreen(
    vm: SpeseViewModel,
    conto: String,
    onBack: () -> Unit,
    onEditSpesa: (Int) -> Unit
) {
    val state by vm.state.collectAsState()

    // Movimenti che riguardano questo conto (come origine o come destinazione di trasferimento)
    val movimenti = remember(state.spese, conto) {
        state.spese.filter { s ->
            s.conto == conto || (s.isTransfer() && s.conto_destinazione == conto)
        }.sortedByDescending { it.data }
    }

    val context = LocalContext.current
    val initialBalance = remember(conto) { InitialBalanceManager.getBalance(context, conto) }
    val saldo = remember(state.spese, conto, initialBalance) {
        state.spese.saldoPerConto(conto, initialBalance)
    }

    val isPositive = saldo >= 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conto) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.padding(padding).fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            state               = rememberLazyListState()
        ) {
            // ── Card saldo ────────────────────────────────────────────
            item(key = "saldo_header") {
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Saldo cumulativo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = String.format(Locale.getDefault(), "%.2f €", saldo),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) Brand else Danger
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${movimenti.size} movimenti totali",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Lista movimenti ───────────────────────────────────────
            if (movimenti.isEmpty()) {
                item(key = "empty") {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Nessun movimento",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Non ci sono movimenti associati a questo conto.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(items = movimenti, key = { it.id }) { spesa ->
                    ContoMovimentoCard(
                        spesa      = spesa,
                        contoCurrent = conto,
                        isDeleting = state.deletingId == spesa.id,
                        onClick    = { if (state.deletingId == null) onEditSpesa(spesa.id) },
                        onDelete   = { vm.delete(spesa.id) }
                    )
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ContoMovimentoCard(
    spesa: SpesaView,
    contoCurrent: String,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isTransfer   = spesa.isTransfer()
    val tipoSafe     = spesa.tipo.orEmpty().ifBlank { "n/d" }
    val dataSafe     = spesa.data.orEmpty().ifBlank { "n/d" }

    // Per i trasferimenti: determina se questo conto è l'origine o la destinazione
    val isTransferIn = isTransfer && spesa.conto_destinazione == contoCurrent

    val isEntrata = when {
        isTransfer  -> isTransferIn  // trasferimento entrante = entrata per questo conto
        else        -> tipoSafe.contains("entrata", ignoreCase = true)
    }

    val categoriaLabel = remember(spesa.categoria, spesa.sottocategoria) {
        listOfNotNull(spesa.categoria, spesa.sottocategoria)
            .joinToString(" • ").ifBlank { if (isTransfer) "Trasferimento" else "Senza categoria" }
    }

    val container   = if (isEntrata) IncomeContainer else ExpenseContainer
    val onContainer = if (isEntrata) OnIncome else OnExpense

    val importoPrefix = if (isEntrata) "+" else "-"

    ElevatedCard(
        onClick   = onClick,
        enabled   = !isDeleting,
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (isTransfer) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = "Trasferimento",
                                tint     = onContainer.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            categoriaLabel,
                            style      = MaterialTheme.typography.titleMedium,
                            color      = onContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dataSafe,
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.7f)
                    )
                    if (isTransfer) {
                        val transferLabel = if (isTransferIn)
                            "Da: ${spesa.conto.orEmpty()}"
                        else
                            "A: ${spesa.conto_destinazione.orEmpty()}"
                        Text(
                            transferLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = onContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    "$importoPrefix${String.format(Locale.getDefault(), "%.2f €", spesa.importo)}",
                    style      = MaterialTheme.typography.titleLarge,
                    color      = onContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label   = { Text(tipoSafe, style = MaterialTheme.typography.labelSmall) },
                    colors  = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.height(28.dp)
                )
                Spacer(Modifier.weight(1f))
                if (isDeleting) {
                    Box(
                        modifier         = Modifier.height(28.dp).padding(horizontal = 8.dp),
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
                        onClick        = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier       = Modifier.height(28.dp)
                    ) {
                        Text(
                            "Elimina",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Note
            if (!spesa.descrizione.isNullOrBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color    = onContainer.copy(alpha = 0.15f)
                )
                Text(
                    spesa.descrizione,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}
