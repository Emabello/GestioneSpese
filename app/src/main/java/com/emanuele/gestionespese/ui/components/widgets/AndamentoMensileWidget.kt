/**
 * AndamentoMensileWidget.kt
 *
 * Widget della dashboard che mostra un grafico a barre dell'andamento delle uscite
 * negli ultimi 6 mesi. La barra del mese corrente è evidenziata con etichetta valore.
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class MonthData(val label: String, val uscite: Double, val isCurrent: Boolean)

@Composable
fun AndamentoMensileWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val fmt   = remember { DateTimeFormatter.ofPattern("MMM", java.util.Locale.ITALIAN) }

    val months = remember(spese, today) {
        (5 downTo 0).map { offset ->
            val month = today.minusMonths(offset.toLong())
            val uscite = spese.filter { s ->
                try {
                    val d = if (s.data?.contains("/") == true)
                        LocalDate.parse(s.data, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    else
                        LocalDate.parse(s.data ?: return@filter false, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    d.year == month.year && d.monthValue == month.monthValue && s.isUscita() && !s.isTransfer()
                } catch (e: Exception) { false }
            }.sumOf { it.importo }
            MonthData(
                label     = month.format(fmt).replaceFirstChar { it.uppercase() },
                uscite    = uscite,
                isCurrent = offset == 0
            )
        }
    }

    val maxVal      = remember(months) { months.maxOfOrNull { it.uscite }?.takeIf { it > 0 } ?: 1.0 }
    val currentMonth = months.lastOrNull()
    val barColor    = Danger
    val barColorDim = Danger.copy(alpha = 0.30f)

    WidgetCard(title = "Andamento mensile (uscite)", modifier = modifier) {
        // Header con totale mese corrente
        if (currentMonth != null) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = String.format(Locale.getDefault(), "%.2f €", currentMonth.uscite),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Danger
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Danger.copy(alpha = 0.10f)
                ) {
                    Text(
                        text     = currentMonth.label,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Danger,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount   = months.size
                val spacing    = 6.dp.toPx()
                val totalSpace = (barCount - 1) * spacing
                val barWidth   = (size.width - totalSpace) / barCount
                val maxHeight  = size.height * 0.90f

                months.forEachIndexed { i, m ->
                    val barH = (m.uscite / maxVal * maxHeight).toFloat().coerceAtLeast(2f)
                    val x    = i * (barWidth + spacing)
                    val y    = size.height - barH

                    drawRoundRect(
                        color        = if (m.isCurrent) barColor else barColorDim,
                        topLeft      = Offset(x, y),
                        size         = Size(barWidth, barH),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }

        // Etichette mesi
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            months.forEach { m ->
                Text(
                    text  = m.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (m.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (m.isCurrent) Danger
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
