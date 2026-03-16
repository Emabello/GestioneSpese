package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Danger
import java.util.Locale

@Composable
fun TotaleUsciteWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val totale = remember(spese) {
        spese.filter { it.isUscita() }.sumOf { it.importo }
    }

    WidgetCard(title = "Uscite ${config.periodo.label()}", modifier = modifier) {
        Text(
            text = String.format(Locale.getDefault(), "%.2f €", totale),
            style = MaterialTheme.typography.headlineMedium,
            color = Danger
        )
    }
}