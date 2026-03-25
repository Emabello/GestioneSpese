/**
 * WidgetBase.kt
 *
 * Composable base condiviso da tutti i widget della dashboard.
 * [WidgetCard] fornisce il contenitore visivo comune (ElevatedCard con titolo
 * e padding uniformi) in cui ogni widget inserisce il proprio contenuto.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Contenitore visivo comune per tutti i widget della dashboard.
 *
 * @param title     Titolo del widget mostrato in cima alla card.
 * @param modifier  Modifier per personalizzare dimensioni e posizione.
 * @param cardColor Colore di sfondo della card (default: surface).
 * @param content   Contenuto del widget, passato come lambda composable.
 */
@Composable
fun WidgetCard(
    title: String,
    modifier: Modifier = Modifier,
    cardColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.elevatedCardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}