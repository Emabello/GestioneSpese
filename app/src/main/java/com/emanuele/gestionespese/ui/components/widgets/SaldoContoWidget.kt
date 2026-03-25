/**
 * SaldoContoWidget.kt
 *
 * Widget della dashboard che mostra il saldo cumulativo (tutto il tempo) di un
 * conto specifico (o del primo conto disponibile se nessuno è configurato).
 * Mostra anche le uscite e le entrate del conto nel mese corrente come contesto.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.WidgetPeriodo
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import java.util.Locale

@Composable
fun SaldoContoWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    // Determina il conto da mostrare: quello configurato o il primo disponibile
    val conto = remember(config.contoFilter, spese) {
        config.contoFilter?.takeIf { it.isNotBlank() }
            ?: spese.mapNotNull { it.conto }.distinct().firstOrNull()
            ?: ""
    }

    val saldo = remember(spese, conto) { spese.saldoPerConto(conto) }

    // Entrate e uscite del conto nel periodo selezionato
    val speseFiltered = remember(spese, config.periodo) { spese.filteredByPeriodo(config.periodo) }
    val entrateContoMese = remember(speseFiltered, conto) {
        speseFiltered.filter { it.conto == conto && it.isEntrata() }.sumOf { it.importo } +
        speseFiltered.filter { it.conto_destinazione == conto && it.isTransfer() }.sumOf { it.importo }
    }
    val usciteContoMese = remember(speseFiltered, conto) {
        speseFiltered.filter { it.conto == conto && it.isUscita() }.sumOf { it.importo } +
        speseFiltered.filter { it.conto == conto && it.isTransfer() }.sumOf { it.importo }
    }

    val contoLabel = if (conto.isNotBlank()) {
        // Se il conto è in formato "ID - Nome", mostra solo la parte dopo " - "
        conto.substringAfter(" - ", conto)
    } else "Nessun conto"

    WidgetCard(title = "Saldo conto", modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = contoLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = String.format(Locale.getDefault(), "%.2f €", saldo),
            style = MaterialTheme.typography.headlineMedium,
            color = if (saldo >= 0) Brand else Danger
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "↑ ${String.format(Locale.getDefault(), "%.2f", entrateContoMese)} €",
                style = MaterialTheme.typography.bodySmall,
                color = Brand
            )
            Text(
                "↓ ${String.format(Locale.getDefault(), "%.2f", usciteContoMese)} €",
                style = MaterialTheme.typography.bodySmall,
                color = Danger
            )
        }
        Text(
            text = "${config.periodo.label()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
