/**
 * ConfrontoMeseWidget.kt
 *
 * Widget della dashboard che confronta le uscite e le entrate del mese corrente
 * rispetto al mese precedente. Mostra la variazione percentuale con icona
 * di tendenza (↑ aumento, ↓ riduzione) e colori semantici.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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

private data class MeseStats(val uscite: Double, val entrate: Double)

private fun List<SpesaView>.statsPerMese(year: Int, month: Int): MeseStats {
    val speseMese = filter { s ->
        try {
            val d = if (s.data?.contains("/") == true)
                LocalDate.parse(s.data, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            else
                LocalDate.parse(s.data ?: return@filter false, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            d.year == year && d.monthValue == month
        } catch (e: Exception) { false }
    }
    return MeseStats(
        uscite  = speseMese.filter { it.isUscita()  && !it.isTransfer() }.sumOf { it.importo },
        entrate = speseMese.filter { it.isEntrata() && !it.isTransfer() }.sumOf { it.importo }
    )
}

private fun variazionePct(corrente: Double, precedente: Double): Double? {
    if (precedente <= 0.0) return null
    return (corrente - precedente) / precedente * 100.0
}

@Composable
fun ConfrontoMeseWidget(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val fmt   = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.ITALIAN) }

    val corrente   = remember(spese, today) { spese.statsPerMese(today.year, today.monthValue) }
    val precedente = remember(spese, today) {
        val prev = today.minusMonths(1)
        spese.statsPerMese(prev.year, prev.monthValue)
    }

    val labelCorrente   = remember(today) { today.format(fmt).replaceFirstChar { it.uppercase() } }
    val labelPrecedente = remember(today) {
        today.minusMonths(1).format(fmt).replaceFirstChar { it.uppercase() }
    }

    val varUscite   = remember(corrente, precedente) { variazionePct(corrente.uscite,   precedente.uscite) }
    val varEntrate  = remember(corrente, precedente) { variazionePct(corrente.entrate,  precedente.entrate) }

    WidgetCard(title = "Confronto mese", modifier = modifier) {
        // Intestazione colonne
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(labelCorrente,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Brand,
                modifier = Modifier.weight(1f))
            Text("vs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(labelPrecedente,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))

        // Riga uscite
        ConfrRow(
            label      = "Uscite",
            corrente   = corrente.uscite,
            precedente = precedente.uscite,
            variazione = varUscite,
            // Spendere meno è positivo → green
            isPositiveGood = false,
            accentColor = Danger
        )

        Spacer(Modifier.height(6.dp))

        // Riga entrate
        ConfrRow(
            label      = "Entrate",
            corrente   = corrente.entrate,
            precedente = precedente.entrate,
            variazione = varEntrate,
            // Guadagnare di più è positivo → green
            isPositiveGood = true,
            accentColor = Brand
        )
    }
}

@Composable
private fun ConfrRow(
    label: String,
    corrente: Double,
    precedente: Double,
    variazione: Double?,
    isPositiveGood: Boolean,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Label + importo corrente
        Column(Modifier.weight(1f)) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                String.format(Locale.getDefault(), "%.0f €", corrente),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }

        // Badge variazione %
        if (variazione != null) {
            val isIncrease = variazione > 0
            val isGood = if (isPositiveGood) isIncrease else !isIncrease
            val badgeColor = if (isGood) Brand else Danger
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = if (isIncrease) Icons.AutoMirrored.Filled.TrendingUp
                                      else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        String.format(Locale.getDefault(), "%.0f%%", kotlin.math.abs(variazione)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeColor
                    )
                }
            }
        }

        // Importo precedente
        Text(
            String.format(Locale.getDefault(), "%.0f €", precedente),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
