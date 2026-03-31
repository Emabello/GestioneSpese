/**
 * AndamentoMensileWidget.kt
 *
 * Widget della dashboard che mostra un grafico a barre dell'andamento mensile
 * negli ultimi 6 mesi. Per ogni mese vengono mostrate due barre affiancate:
 * - Verde (Brand): entrate del mese
 * - Rosso (Danger): uscite del mese
 * La coppia del mese corrente è evidenziata con etichetta valore in header.
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

private data class MonthData(
    val label: String,
    val uscite: Double,
    val entrate: Double,
    val isCurrent: Boolean
)

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
            MonthData(
                label     = month.format(fmt).replaceFirstChar { it.uppercase() },
                uscite    = uscite,
                entrate   = entrate,
                isCurrent = offset == 0
            )
        }
    }

    val maxVal       = remember(months) { months.maxOfOrNull { maxOf(it.uscite, it.entrate) }?.takeIf { it > 0 } ?: 1.0 }
    val currentMonth = months.lastOrNull()

    WidgetCard(title = "Andamento mensile", modifier = modifier) {
        // Header con totale mese corrente
        if (currentMonth != null) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Entrate mese corrente
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Brand.copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "↑ ${String.format(Locale.getDefault(), "%.0f €", currentMonth.entrate)}",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = Brand
                        )
                    }
                }
                // Uscite mese corrente
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Danger.copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "↓ ${String.format(Locale.getDefault(), "%.0f €", currentMonth.uscite)}",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = Danger
                        )
                    }
                }
                // Etichetta mese
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text     = currentMonth.label,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // S: solo header, niente grafico
        if (config.heightStep == WidgetHeightStep.S) return@WidgetCard

        val brandColor    = Brand
        val brandColorDim = Brand.copy(alpha = 0.30f)
        val dangerColor   = Danger
        val dangerColorDim = Danger.copy(alpha = 0.30f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount    = months.size
                val groupSpacing = 8.dp.toPx()   // spazio tra gruppi di mesi
                val barGap      = 2.dp.toPx()    // spazio tra le due barre dello stesso mese
                val totalGroupSpace = (barCount - 1) * groupSpacing
                val groupWidth  = (size.width - totalGroupSpace) / barCount
                val barWidth    = (groupWidth - barGap) / 2f
                val maxHeight   = size.height * 0.90f

                months.forEachIndexed { i, m ->
                    val groupX = i * (groupWidth + groupSpacing)

                    // Barra entrate (sinistra, verde)
                    val entrateH = (m.entrate / maxVal * maxHeight).toFloat().coerceAtLeast(2f)
                    val entrateY = size.height - entrateH
                    drawRoundRect(
                        color        = if (m.isCurrent) brandColor else brandColorDim,
                        topLeft      = Offset(groupX, entrateY),
                        size         = Size(barWidth, entrateH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )

                    // Barra uscite (destra, rossa)
                    val usciteH = (m.uscite / maxVal * maxHeight).toFloat().coerceAtLeast(2f)
                    val usciteY = size.height - usciteH
                    drawRoundRect(
                        color        = if (m.isCurrent) dangerColor else dangerColorDim,
                        topLeft      = Offset(groupX + barWidth + barGap, usciteY),
                        size         = Size(barWidth, usciteH),
                        cornerRadius = CornerRadius(3.dp.toPx())
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
                    color = if (m.isCurrent) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Legenda
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = brandColor) }
                Text("Entrate", style = MaterialTheme.typography.labelSmall, color = Brand)
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = dangerColor) }
                Text("Uscite", style = MaterialTheme.typography.labelSmall, color = Danger)
            }
        }

        // L: tabella mensile
        if (config.heightStep == WidgetHeightStep.L) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(4.dp))
            months.forEach { m ->
                val delta = m.entrate - m.uscite
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        m.label,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = if (m.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color      = if (m.isCurrent) MaterialTheme.colorScheme.onSurface
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier   = Modifier.width(36.dp)
                    )
                    Text(
                        String.format(Locale.getDefault(), "%.0f €", m.entrate),
                        style = MaterialTheme.typography.labelSmall,
                        color = Brand
                    )
                    Text(
                        String.format(Locale.getDefault(), "%.0f €", m.uscite),
                        style = MaterialTheme.typography.labelSmall,
                        color = Danger
                    )
                    Text(
                        text  = "${if (delta >= 0) "+" else ""}${String.format(Locale.getDefault(), "%.0f €", delta)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (delta >= 0) Brand else Danger
                    )
                }
            }
        }
    }
}
