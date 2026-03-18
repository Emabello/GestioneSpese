/**
 * TopCategorieWidget.kt
 *
 * Widget della dashboard che mostra la classifica delle categorie per importo
 * totale di uscite nel periodo selezionato, limitata alle prime [topN] voci.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
import java.util.Locale

@Composable
fun TopCategorieWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    // spese arriva già filtrata per mese — rimuovi filteredByPeriodo
    val top = remember(spese, config.topN) {
        spese
            .filter { it.isUscita() }   // ← usa isUscita() con fallback
            .groupBy { it.categoria?.trim() ?: "Senza categoria" }
            .mapValues { (_, v) -> v.sumOf { it.importo } }
            .entries.sortedByDescending { it.value }
            .take(config.topN)
    }
    val maxVal = top.firstOrNull()?.value ?: 1.0

    WidgetCard(title = "Top ${config.topN} categorie", modifier = modifier) {
        if (top.isEmpty()) {
            Text(
                "Nessun dato",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            top.forEach { (cat, totale) ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        cat,
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        String.format(Locale.getDefault(), "%.0f €", totale),
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand
                    )
                }
                LinearProgressIndicator(
                    progress   = { (totale / maxVal).toFloat() },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    color      = Brand,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}