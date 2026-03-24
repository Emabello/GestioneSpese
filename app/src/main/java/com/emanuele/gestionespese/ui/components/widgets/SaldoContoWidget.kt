package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.Danger
import java.util.Locale

@Composable
fun SaldoContoWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val conto = config.contoFilter
    val movimentiConto = remember(spese, conto) {
        if (conto.isNullOrBlank()) emptyList() else spese.filter { it.conto == conto }
    }
    val entrate = remember(movimentiConto) { movimentiConto.filter { it.isEntrata() }.sumOf { it.importo } }
    val uscite = remember(movimentiConto) { movimentiConto.filter { it.isUscita() }.sumOf { it.importo } }
    val saldo = entrate - uscite

    WidgetCard(
        title = if (conto.isNullOrBlank()) "Saldo conto" else "Saldo ${conto.trim()}",
        modifier = modifier
    ) {
        if (conto.isNullOrBlank()) {
            Text("Configura un conto dal pulsante ⚙", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Entrate", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+ ${String.format(Locale.getDefault(), "%.2f", entrate)} €", color = Brand)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Uscite", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("- ${String.format(Locale.getDefault(), "%.2f", uscite)} €", color = Danger)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Saldo", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${String.format(Locale.getDefault(), "%.2f", saldo)} €",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (saldo >= 0) Brand else Danger
                )
            }
        }
    }
}
