package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun AndamentoMensileWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
    val buckets = remember(spese) {
        val today = LocalDate.now().withDayOfMonth(1)
        (5 downTo 0).map { today.minusMonths(it.toLong()) }.map { month ->
            val movimenti = spese.filter { it.data.orEmpty().startsWith(month.format(formatter)) }
            val saldo = movimenti.filter { it.isEntrata() }.sumOf { it.importo } -
                    movimenti.filter { it.isUscita() }.sumOf { it.importo }
            month to saldo
        }
    }
    val maxAbs = buckets.maxOfOrNull { abs(it.second) }?.takeIf { it > 0.0 } ?: 1.0

    WidgetCard(title = "Andamento ultimi 6 mesi", modifier = modifier) {
        buckets.forEach { (month, saldo) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(month.month.name.take(3), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    String.format("%.0f €", saldo),
                    color = if (saldo >= 0) Brand else Danger
                )
            }
            LinearProgressIndicator(
                progress = { (abs(saldo) / maxAbs).toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (saldo >= 0) Brand else Danger,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
