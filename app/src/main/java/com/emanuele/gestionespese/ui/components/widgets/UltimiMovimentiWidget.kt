/**
 * UltimiMovimentiWidget.kt
 *
 * Widget della dashboard che mostra gli ultimi [topN] movimenti del periodo
 * selezionato, con data, descrizione e importo formattato.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
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
import com.emanuele.gestionespese.ui.theme.Danger
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
                val isEntrata = spesa.isEntrata()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            spesa.categoria?.trim() ?: "—",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            spesa.data ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${if (isEntrata) "+" else "-"} ${String.format(Locale.getDefault(), "%.2f", spesa.importo)} €",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isEntrata) Brand else Danger
                    )
                }
                if (idx < ultimi.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
