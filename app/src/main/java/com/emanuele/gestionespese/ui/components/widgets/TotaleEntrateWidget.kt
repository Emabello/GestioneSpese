/**
 * TotaleEntrateWidget.kt
 *
 * Widget della dashboard che mostra il totale delle entrate nel periodo selezionato.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
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
import com.emanuele.gestionespese.ui.theme.IncomeContainer
import java.util.Locale

@Composable
fun TotaleEntrateWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val totale = remember(spese) {
        spese.filter { it.isEntrata() && !it.isTransfer() }.sumOf { it.importo }
    }

    WidgetCard(
        title     = "Entrate",
        modifier  = modifier,
        cardColor = IncomeContainer
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            Column {
                Text(
                    text       = String.format(Locale.getDefault(), "%.2f €", totale),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Brand
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Brand.copy(alpha = 0.12f)
                ) {
                    Text(
                        text     = config.periodo.label(),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Brand,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                imageVector        = Icons.Default.TrendingUp,
                contentDescription = null,
                tint               = Brand.copy(alpha = 0.35f),
                modifier           = Modifier.size(32.dp)
            )
        }
    }
}
