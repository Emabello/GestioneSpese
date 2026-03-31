/**
 * RisparmioCumulativoWidget.kt
 *
 * Widget della dashboard che mostra il risparmio netto mensile (entrate − uscite)
 * degli ultimi 6 mesi sotto forma di grafico lineare con area riempita.
 * - Linea e fill verde se il risparmio netto totale è positivo, rossa se negativo.
 * - Header: risparmio cumulativo totale degli ultimi 6 mesi + variazione mese corrente.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.WidgetHeightStep
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class SavingsPoint(val label: String, val risparmio: Double, val isCurrent: Boolean)

@Composable
fun RisparmioCumulativoWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val fmt   = remember { DateTimeFormatter.ofPattern("MMM", java.util.Locale.ITALIAN) }

    val points = remember(spese, today) {
        (5 downTo 0).map { offset ->
            val month = today.minusMonths(offset.toLong())
            val speseDelMese = spese.filter { s ->
                try {
                    val d = if (s.data?.contains("/") == true)
                        LocalDate.parse(s.data, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    else
                        LocalDate.parse(s.data ?: return@filter false, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    d.year == month.year && d.monthValue == month.monthValue
                } catch (e: Exception) { false }
            }
            val uscite  = speseDelMese.filter { it.isUscita()  && !it.isTransfer() }.sumOf { it.importo }
            val entrate = speseDelMese.filter { it.isEntrata() && !it.isTransfer() }.sumOf { it.importo }
            SavingsPoint(
                label      = month.format(fmt).replaceFirstChar { it.uppercase() },
                risparmio  = entrate - uscite,
                isCurrent  = offset == 0
            )
        }
    }

    val totale6Mesi    = remember(points) { points.sumOf { it.risparmio } }
    val isPositive     = totale6Mesi >= 0
    val currentPoint   = points.lastOrNull()
    val prevPoint      = points.getOrNull(points.size - 2)

    val lineColor      = if (isPositive) Brand else Danger

    WidgetCard(title = "Risparmio cumulativo", modifier = modifier) {
        // Header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    String.format(Locale.getDefault(), "%.2f €", totale6Mesi),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = lineColor
                )
                Text(
                    "Ultimi 6 mesi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Badge mese corrente
            if (currentPoint != null) {
                val curIsPos = currentPoint.risparmio >= 0
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = (if (curIsPos) Brand else Danger).copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "${if (curIsPos) "+" else ""}${String.format(Locale.getDefault(), "%.0f €", currentPoint.risparmio)}",
                        style    = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color    = if (curIsPos) Brand else Danger,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // S: solo header, niente grafico
        if (config.heightStep == WidgetHeightStep.S) return@WidgetCard

        Spacer(Modifier.height(8.dp))

        // Calcola range per normalizzazione
        val maxAbs = remember(points) { points.maxOfOrNull { kotlin.math.abs(it.risparmio) }?.takeIf { it > 0 } ?: 1.0 }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (points.size < 2) return@Canvas

                val pointCount = points.size
                val stepX = size.width / (pointCount - 1).toFloat()
                val midY  = size.height / 2f

                // Calcola coordinate Y per ogni punto (0 = centro)
                fun yFor(risparmio: Double): Float {
                    val norm = (risparmio / maxAbs).toFloat().coerceIn(-1f, 1f)
                    return midY - norm * (midY * 0.85f)
                }

                val coords = points.mapIndexed { i, p -> Offset(i * stepX, yFor(p.risparmio)) }

                // Path della linea
                val linePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    coords.drop(1).forEach { lineTo(it.x, it.y) }
                }

                // Path del fill (chiuso verso la linea zero)
                val fillPath = Path().apply {
                    moveTo(coords.first().x, midY)
                    coords.forEach { lineTo(it.x, it.y) }
                    lineTo(coords.last().x, midY)
                    close()
                }

                // Linea zero (tratteggiata)
                drawLine(
                    color       = lineColor.copy(alpha = 0.20f),
                    start       = Offset(0f, midY),
                    end         = Offset(size.width, midY),
                    strokeWidth = 1.dp.toPx()
                )

                // Fill area sotto la curva
                drawPath(
                    path  = fillPath,
                    color = lineColor.copy(alpha = 0.12f),
                    style = Fill
                )

                // Linea principale
                drawPath(
                    path  = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Punto evidenziato sull'ultimo mese
                val last = coords.last()
                drawCircle(
                    color  = lineColor,
                    radius = 4.dp.toPx(),
                    center = last
                )
                drawCircle(
                    color  = lineColor.copy(alpha = 0.20f),
                    radius = 7.dp.toPx(),
                    center = last
                )
            }
        }

        // Etichette mesi
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { p ->
                Text(
                    text       = p.label,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = if (p.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color      = if (p.isCurrent) lineColor
                                 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // L: tabella mese per mese con cumulativo progressivo
        if (config.heightStep == WidgetHeightStep.L) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(4.dp))
            var cumulative = 0.0
            points.forEach { p ->
                cumulative += p.risparmio
                val isPos = p.risparmio >= 0
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        p.label,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = if (p.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color      = if (p.isCurrent) lineColor
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier   = Modifier.width(36.dp)
                    )
                    Text(
                        text  = "${if (isPos) "+" else ""}${String.format(Locale.getDefault(), "%.0f €", p.risparmio)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPos) Brand else Danger
                    )
                    Text(
                        text  = String.format(Locale.getDefault(), "%.0f €", cumulative),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cumulative >= 0) Brand else Danger
                    )
                }
            }
        }
    }
}
