/**
 * GraficoTortaWidget.kt
 *
 * Widget della dashboard che visualizza un grafico a torta (donut chart) delle
 * spese suddivise per categoria nel periodo selezionato. Usa Canvas per il
 * disegno diretto degli archi colorati.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import java.util.Locale

private val TORTA_COLORS = listOf(
    Color(0xFF2AA39A), Color(0xFF1E7F78), Color(0xFF4CAF50),
    Color(0xFFFFC107), Color(0xFFFF5722), Color(0xFF9C27B0),
    Color(0xFF2196F3), Color(0xFFFF9800)
)

@Composable
fun GraficoTortaWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    // spese arriva già filtrata per mese dalla SummaryScreen
    val slices = remember(spese) {
        spese
            .filter { it.isUscita() }   // ← usa tipo_movimento
            .groupBy { it.categoria?.trim() ?: "Altro" }
            .mapValues { (_, v) -> v.sumOf { it.importo } }
            .entries.sortedByDescending { it.value }
            .take(8)
    }
    val totale = slices.sumOf { it.value }

    WidgetCard(title = "Distribuzione uscite", modifier = modifier) {
        if (slices.isEmpty()) {
            Text(
                "Nessun dato",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@WidgetCard
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                var startAngle = -90f
                slices.forEachIndexed { idx, (_, valore) ->
                    val sweep = ((valore / totale) * 360f).toFloat()
                    drawArc(
                        color      = TORTA_COLORS[idx % TORTA_COLORS.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter  = true,
                        topLeft    = Offset.Zero,
                        size       = Size(size.width, size.height)
                    )
                    startAngle += sweep
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                slices.forEachIndexed { idx, (cat, valore) ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            drawCircle(color = TORTA_COLORS[idx % TORTA_COLORS.size])
                        }
                        Text(
                            "${cat.take(14)} ${String.format(Locale.getDefault(), "%.0f%%", (valore / totale) * 100)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}