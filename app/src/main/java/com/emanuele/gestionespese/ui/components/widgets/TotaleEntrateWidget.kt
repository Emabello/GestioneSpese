/**
 * TotaleEntrateWidget.kt
 *
 * Widget della dashboard che mostra il totale delle entrate nel periodo selezionato.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
import java.util.Locale

@Composable
fun TotaleEntrateWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val totale = remember(spese) {
        spese.filter { it.isEntrata() }.sumOf { it.importo }
    }

    WidgetCard(title = "Entrate ${config.periodo.label()}", modifier = modifier) {
        Text(
            text = String.format(Locale.getDefault(), "%.2f €", totale),
            style = MaterialTheme.typography.headlineMedium,
            color = Brand
        )
    }
}