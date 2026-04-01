/**
 * TotaleUsciteWidget.kt
 *
 * Widget della dashboard che mostra il totale delle uscite nel periodo selezionato.
 * Layout adattivo in base a heightStep:
 * - S: importo + badge periodo + icona
 * - M: + confronto % con periodo precedente
 * - L: + breakdown top 3 categorie
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.WidgetHeightStep
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import com.emanuele.gestionespese.ui.theme.expenseContainer
import java.util.Locale

@Composable
fun TotaleUsciteWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val filtered = remember(spese, config.periodo) { spese.filteredByPeriodo(config.periodo) }
    val totale   = remember(filtered) {
        filtered.filter { it.isUscita() && !it.isTransfer() }.sumOf { it.importo }
    }

    // M/L: confronto con periodo precedente
    val prevFiltered = remember(spese, config.periodo) { spese.filteredPrevPeriodo(config.periodo) }
    val totalePrev   = remember(prevFiltered) {
        prevFiltered.filter { it.isUscita() && !it.isTransfer() }.sumOf { it.importo }
    }
    val delta = remember(totale, totalePrev) {
        if (totalePrev > 0) ((totale - totalePrev) / totalePrev * 100).toInt() else Int.MIN_VALUE
    }

    WidgetCard(
        title     = "Uscite",
        modifier  = modifier,
        cardColor = MaterialTheme.expenseContainer
    ) {
        // S: layout base — numero + badge + icona
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            Column {
                Text(
                    text       = String.format(Locale.getDefault(), "%.2f €", totale),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Danger
                )
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = Danger.copy(alpha = 0.12f)) {
                    Text(
                        text     = config.periodo.label(),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Danger,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                imageVector        = Icons.Default.TrendingDown,
                contentDescription = null,
                tint               = Danger.copy(alpha = 0.35f),
                modifier           = Modifier.size(32.dp)
            )
        }

        // M+: riga confronto con periodo precedente
        if (config.heightStep.ordinal >= WidgetHeightStep.M.ordinal && delta != Int.MIN_VALUE) {
            Spacer(Modifier.height(8.dp))
            val isWorse = delta > 0
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector        = if (isWorse) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint               = if (isWorse) Danger else Brand,
                    modifier           = Modifier.size(14.dp)
                )
                Text(
                    text  = "${if (delta >= 0) "+" else ""}$delta% vs periodo prec.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWorse) Danger else Brand
                )
            }
        }

        // L: top 3 categorie
        if (config.heightStep == WidgetHeightStep.L) {
            val top3 = remember(filtered) {
                filtered.filter { it.isUscita() && !it.isTransfer() }
                    .groupBy { it.categoria?.trim() ?: "Altro" }
                    .mapValues { (_, v) -> v.sumOf { it.importo } }
                    .entries.sortedByDescending { it.value }.take(3)
            }
            if (top3.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Danger.copy(alpha = 0.15f), thickness = 0.5.dp)
                Spacer(Modifier.height(4.dp))
                top3.forEach { (cat, valore) ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            cat.take(18),
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            String.format(Locale.getDefault(), "%.0f €", valore),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = Danger
                        )
                    }
                }
            }
        }
    }
}
