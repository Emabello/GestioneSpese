/**
 * GraficoTortaWidget.kt
 *
 * Widget della dashboard che visualizza un grafico a torta (donut chart) delle
 * spese suddivise per categoria nel periodo selezionato. Usa Canvas per il
 * disegno diretto degli archi colorati. Mostra il totale al centro del donut.
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
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
    val slices = remember(spese, config.periodo) {
        spese.filteredByPeriodo(config.periodo)
            .filter { it.isUscita() }
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

        val textColor    = MaterialTheme.colorScheme.onSurface.toArgb()
        val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        val brandArgb    = Brand.toArgb()
        val bgColor      = MaterialTheme.colorScheme.surface

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Donut chart con totale al centro
            Box(
                modifier          = Modifier.size(120.dp),
                contentAlignment  = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(120.dp)) {
                    val strokeWidth = 22.dp.toPx()
                    val inset       = strokeWidth / 2f
                    val arcSize     = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft     = Offset(inset, inset)
                    var startAngle  = -90f
                    slices.forEachIndexed { idx, (_, valore) ->
                        val sweep = ((valore / totale) * 360f).toFloat()
                        drawArc(
                            color      = TORTA_COLORS[idx % TORTA_COLORS.size],
                            startAngle = startAngle,
                            sweepAngle = sweep - 1f, // gap tra le fette
                            useCenter  = false,
                            topLeft    = topLeft,
                            size       = arcSize,
                            style      = Stroke(width = strokeWidth)
                        )
                        startAngle += sweep
                    }
                }
                // Testo al centro
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = String.format(Locale.getDefault(), "%.0f", totale),
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = Brand
                    )
                    Text(
                        text  = "€",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Legenda: dot + nome + percentuale
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier            = Modifier.weight(1f)
            ) {
                slices.forEachIndexed { idx, (cat, valore) ->
                    val pct = if (totale > 0) (valore / totale * 100).toInt() else 0
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Canvas(modifier = Modifier.size(8.dp)) {
                            drawCircle(color = TORTA_COLORS[idx % TORTA_COLORS.size])
                        }
                        Text(
                            text     = cat.take(13),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            text  = "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TORTA_COLORS[idx % TORTA_COLORS.size]
                        )
                    }
                }
            }
        }
    }
}
