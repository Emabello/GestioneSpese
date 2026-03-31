/**
 * SaldoMeseWidget.kt
 *
 * Widget della dashboard che mostra il saldo netto del periodo selezionato
 * (entrate – uscite). Il valore è colorato in verde se positivo, rosso se negativo.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import com.emanuele.gestionespese.ui.theme.expenseContainer
import com.emanuele.gestionespese.ui.theme.incomeContainer
import java.util.Locale

@Composable
fun SaldoMeseWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val filtered   = remember(spese, config.periodo) { spese.filteredByPeriodo(config.periodo) }
    val entrate    = remember(filtered) { filtered.filter { it.isEntrata() }.sumOf { it.importo } }
    val uscite     = remember(filtered) { filtered.filter { it.isUscita() }.sumOf { it.importo } }
    val saldo      = entrate - uscite
    val isPositive = saldo >= 0

    WidgetCard(
        title     = "Saldo ${config.periodo.label()}",
        modifier  = modifier,
        cardColor = if (isPositive) MaterialTheme.incomeContainer else MaterialTheme.expenseContainer
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = String.format(Locale.getDefault(), "%.2f €", saldo),
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = if (isPositive) Brand else Danger
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = (if (isPositive) Brand else Danger).copy(alpha = 0.10f)
            ) {
                Text(
                    text     = if (isPositive) "+" else "−",
                    style    = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color    = if (isPositive) Brand else Danger,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(8.dp),
                color    = Brand.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, null,
                        tint = Brand, modifier = Modifier.size(12.dp))
                    Text(
                        String.format(Locale.getDefault(), "%.0f €", entrate),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Brand
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(8.dp),
                color    = Danger.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, null,
                        tint = Danger, modifier = Modifier.size(12.dp))
                    Text(
                        String.format(Locale.getDefault(), "%.0f €", uscite),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Danger
                    )
                }
            }
        }
    }
}
