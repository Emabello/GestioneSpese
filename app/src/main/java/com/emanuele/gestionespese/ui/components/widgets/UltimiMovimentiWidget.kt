/**
 * UltimiMovimentiWidget.kt
 *
 * Widget della dashboard che mostra gli ultimi [topN] movimenti del periodo
 * selezionato, con data, descrizione e importo formattato.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UltimiMovimentiWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val ultimi = remember(spese, config.topN) {
        spese.sortedByDescending { it.data }.take(config.topN)
    }

    WidgetCard(title = "Ultimi ${config.topN} movimenti", modifier = modifier) {
        if (ultimi.isEmpty()) {
            Text("Nessun movimento", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ultimi.forEachIndexed { idx, spesa ->
                val isEntrata = spesa.isEntrata() && !spesa.isTransfer()
                val sign      = when {
                    spesa.isTransfer() -> "⇄"
                    isEntrata          -> "+"
                    else               -> "−"
                }
                val amountColor = when {
                    spesa.isTransfer() -> MaterialTheme.colorScheme.onSurfaceVariant
                    isEntrata          -> Brand
                    else               -> Danger
                }
                val bgColor = when {
                    spesa.isTransfer() -> MaterialTheme.colorScheme.surfaceVariant
                    isEntrata          -> Brand.copy(alpha = 0.10f)
                    else               -> Danger.copy(alpha = 0.10f)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            spesa.categoria?.trim() ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            formatDataBreve(spesa.data),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bgColor
                    ) {
                        Text(
                            text = "$sign ${String.format(Locale.getDefault(), "%.2f", spesa.importo)} €",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = amountColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (idx < ultimi.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

private fun formatDataBreve(data: String?): String {
    if (data.isNullOrBlank()) return "—"
    return try {
        val today = LocalDate.now()
        val d = if (data.contains("/"))
            LocalDate.parse(data, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        else
            LocalDate.parse(data, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        when {
            d == today               -> "Oggi"
            d == today.minusDays(1)  -> "Ieri"
            else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))
        }
    } catch (e: Exception) { data }
}
