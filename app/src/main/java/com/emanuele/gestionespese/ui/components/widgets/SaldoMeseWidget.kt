package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import java.util.Locale

@Composable
fun SaldoMeseWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val entrate = remember(spese) { spese.filter { it.isEntrata() }.sumOf { it.importo } }
    val uscite  = remember(spese) { spese.filter { it.isUscita()  }.sumOf { it.importo } }

    val saldo     = entrate - uscite
    val isPositive = saldo >= 0

    WidgetCard(title = "Saldo ${config.periodo.label()}", modifier = modifier) {
        Text(
            text = String.format(Locale.getDefault(), "%.2f €", saldo),
            style = MaterialTheme.typography.headlineMedium,
            color = if (isPositive) Brand else Danger
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "↑ ${String.format(Locale.getDefault(), "%.2f", entrate)} €",
                style = MaterialTheme.typography.bodySmall,
                color = Brand
            )
            Text(
                "↓ ${String.format(Locale.getDefault(), "%.2f", uscite)} €",
                style = MaterialTheme.typography.bodySmall,
                color = Danger
            )
        }
    }
}