/**
 * TopCategorieWidget.kt
 *
 * Widget della dashboard che mostra la classifica delle categorie per importo
 * totale di uscite nel periodo selezionato, limitata alle prime [topN] voci.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import java.util.Locale

private val RANK_COLORS = listOf(
    Color(0xFFFFD700), // oro
    Color(0xFFC0C0C0), // argento
    Color(0xFFCD7F32), // bronzo
)

@Composable
fun TopCategorieWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val top = remember(spese, config.topN, config.periodo) {
        spese.filteredByPeriodo(config.periodo)
            .filter { it.isUscita() }
            .groupBy { it.categoria?.trim() ?: "Senza categoria" }
            .mapValues { (_, v) -> v.sumOf { it.importo } }
            .entries.sortedByDescending { it.value }
            .take(config.topN)
    }
    val maxVal = top.firstOrNull()?.value ?: 1.0
    val totale = top.sumOf { it.value }

    WidgetCard(title = "Top ${config.topN} categorie", modifier = modifier) {
        if (top.isEmpty()) {
            Text(
                "Nessun dato",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            top.forEachIndexed { idx, (cat, totCat) ->
                val pct = if (totale > 0) (totCat / totale * 100).toInt() else 0
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge rank
                    Surface(
                        shape = CircleShape,
                        color = when {
                            idx < RANK_COLORS.size -> RANK_COLORS[idx].copy(alpha = 0.18f)
                            else                   -> Brand.copy(alpha = 0.12f)
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text  = "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    idx < RANK_COLORS.size -> RANK_COLORS[idx]
                                    else                   -> Brand
                                }
                            )
                        }
                    }
                    // Nome categoria
                    Text(
                        text     = cat,
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    // Importo + percentuale
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            String.format(Locale.getDefault(), "%.0f €", totCat),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Danger
                        )
                        Text(
                            "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LinearProgressIndicator(
                    progress   = { (totCat / maxVal).toFloat() },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .padding(bottom = 2.dp),
                    color      = if (idx == 0) Danger else Brand,
                    trackColor = Brand.copy(alpha = 0.10f)
                )
            }
        }
    }
}
