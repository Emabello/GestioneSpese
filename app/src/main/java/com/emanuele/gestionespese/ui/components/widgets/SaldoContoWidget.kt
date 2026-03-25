/**
 * SaldoContoWidget.kt
 *
 * Widget della dashboard che mostra il saldo cumulativo (tutto il tempo) di un
 * conto specifico (o del primo conto disponibile se nessuno è configurato).
 * Mostra anche le uscite e le entrate del conto nel periodo come contesto.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import com.emanuele.gestionespese.ui.theme.expenseContainer
import com.emanuele.gestionespese.ui.theme.incomeContainer
import com.emanuele.gestionespese.utils.InitialBalanceManager
import java.util.Locale

@Composable
fun SaldoContoWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val conto = remember(config.contoFilter, spese) {
        config.contoFilter?.takeIf { it.isNotBlank() }
            ?: spese.mapNotNull { it.conto }.distinct().firstOrNull()
            ?: ""
    }

    val context = LocalContext.current
    val initialBalance = remember(conto) { InitialBalanceManager.getBalance(context, conto) }
    val saldo = remember(spese, conto, initialBalance) { spese.saldoPerConto(conto, initialBalance) }
    val isPositive = saldo >= 0

    val speseFiltered    = remember(spese, config.periodo) { spese.filteredByPeriodo(config.periodo) }
    val entrateContoMese = remember(speseFiltered, conto) {
        speseFiltered.filter { it.conto == conto && it.isEntrata() }.sumOf { it.importo } +
        speseFiltered.filter { it.conto_destinazione == conto && it.isTransfer() }.sumOf { it.importo }
    }
    val usciteContoMese = remember(speseFiltered, conto) {
        speseFiltered.filter { it.conto == conto && it.isUscita() }.sumOf { it.importo } +
        speseFiltered.filter { it.conto == conto && it.isTransfer() }.sumOf { it.importo }
    }

    val contoLabel = if (conto.isNotBlank()) conto.substringAfter(" - ", conto) else "Nessun conto"

    WidgetCard(
        title     = "Saldo conto",
        modifier  = modifier,
        cardColor = if (isPositive) MaterialTheme.incomeContainer else MaterialTheme.expenseContainer
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.AccountBalance,
                contentDescription = null,
                tint               = if (isPositive) Brand else Danger,
                modifier           = Modifier.size(14.dp)
            )
            Text(
                text  = contoLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text       = String.format(Locale.getDefault(), "%.2f €", saldo),
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color      = if (isPositive) Brand else Danger
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Brand.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, null,
                        tint = Brand, modifier = Modifier.size(10.dp))
                    Text(
                        String.format(Locale.getDefault(), "%.0f €", entrateContoMese),
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Danger.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, null,
                        tint = Danger, modifier = Modifier.size(10.dp))
                    Text(
                        String.format(Locale.getDefault(), "%.0f €", usciteContoMese),
                        style = MaterialTheme.typography.labelSmall,
                        color = Danger
                    )
                }
            }
        }
    }
}
