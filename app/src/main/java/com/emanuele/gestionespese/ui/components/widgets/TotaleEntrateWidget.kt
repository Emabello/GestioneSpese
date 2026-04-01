/**
 * TotaleEntrateWidget.kt
 *
 * Widget della dashboard che mostra il totale delle entrate nel mese selezionato.
 * Layout adattivo in base a heightStep:
 * - S: importo + icona
 * - M: + confronto % con mese precedente
 * - L: + breakdown top 3 fonti di entrata
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
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
import com.emanuele.gestionespese.ui.theme.incomeContainer
import java.util.Locale

@Composable
fun TotaleEntrateWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    spesePrevMonth: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val totale = remember(spese) {
        spese.filter { it.isEntrata() && !it.isTransfer() }.sumOf { it.importo }
    }

    val totalePrev = remember(spesePrevMonth) {
        spesePrevMonth.filter { it.isEntrata() && !it.isTransfer() }.sumOf { it.importo }
    }
    val delta = remember(totale, totalePrev) {
        if (totalePrev > 0) ((totale - totalePrev) / totalePrev * 100).toInt() else Int.MIN_VALUE
    }

    WidgetCard(
        title     = "Entrate",
        modifier  = modifier,
        cardColor = MaterialTheme.incomeContainer
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            Text(
                text       = String.format(Locale.getDefault(), "%.2f €", totale),
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = Brand
            )
            Icon(
                imageVector        = Icons.Default.TrendingUp,
                contentDescription = null,
                tint               = Brand.copy(alpha = 0.35f),
                modifier           = Modifier.size(32.dp)
            )
        }

        // M+: riga confronto con mese precedente
        if (config.heightStep.ordinal >= WidgetHeightStep.M.ordinal && delta != Int.MIN_VALUE) {
            Spacer(Modifier.height(8.dp))
            val isGood = delta > 0
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector        = if (isGood) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint               = if (isGood) Brand else Danger,
                    modifier           = Modifier.size(14.dp)
                )
                Text(
                    text  = "${if (delta >= 0) "+" else ""}$delta% vs mese prec.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isGood) Brand else Danger
                )
            }
        }

        // L: top 3 fonti di entrata
        if (config.heightStep == WidgetHeightStep.L) {
            val top3 = remember(spese) {
                spese.filter { it.isEntrata() && !it.isTransfer() }
                    .groupBy { it.categoria?.trim() ?: "Altro" }
                    .mapValues { (_, v) -> v.sumOf { it.importo } }
                    .entries.sortedByDescending { it.value }.take(3)
            }
            if (top3.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Brand.copy(alpha = 0.15f), thickness = 0.5.dp)
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
                            color      = Brand
                        )
                    }
                }
            }
        }
    }
}
