package com.emanuele.gestionespese.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emanuele.gestionespese.utils.WizardSelection

// Colori per le etichette nel testo
private val colorImporto   = Color(0xFF3949AB) // Indigo
private val colorEsercente = Color(0xFF388E3C) // Verde

/**
 * Composable che mostra [text] con selezione drag nativa di Android.
 * Quando l'utente seleziona del testo appare una toolbar flottante con
 * i pulsanti IMPORTO e ESERCENTE per etichettare la selezione.
 *
 * Le selezioni già assegnate vengono evidenziate con sfondo colorato.
 *
 * @param text             Testo completo della notifica.
 * @param selections       Selezioni già etichettate (vengono evidenziate).
 * @param onLabelAssigned  Callback chiamata quando l'utente assegna un'etichetta a una selezione.
 */
@Composable
fun SelectableNotificationText(
    text: String,
    selections: List<WizardSelection>,
    onLabelAssigned: (start: Int, end: Int, label: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // AnnotatedString con i colori delle selezioni esistenti
    val annotatedString = remember(text, selections) {
        buildAnnotatedString {
            var cursor = 0
            // Ordina le selezioni per start
            val sorted = selections.sortedBy { it.start }
            for (sel in sorted) {
                val s = sel.start.coerceIn(0, text.length)
                val e = sel.end.coerceIn(s, text.length)
                if (cursor < s) append(text.substring(cursor, s))
                withStyle(
                    SpanStyle(
                        background = when (sel.label) {
                            "IMPORTO"   -> colorImporto.copy(alpha = 0.25f)
                            "ESERCENTE" -> colorEsercente.copy(alpha = 0.25f)
                            else        -> Color.Transparent
                        },
                        color = when (sel.label) {
                            "IMPORTO"   -> colorImporto
                            "ESERCENTE" -> colorEsercente
                            else        -> Color.Unspecified
                        }
                    )
                ) { append(text.substring(s, e)) }
                cursor = e
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    // Stato selezione corrente
    var currentSelection by remember { mutableStateOf(TextRange(0, 0)) }
    // Visibilità toolbar custom
    var showToolbar by remember { mutableStateOf(false) }

    // TextToolbar personalizzata: intercetta il momento in cui Android
    // vorrebbe mostrare il menu nativo (copia/incolla), e invece mostriamo
    // i nostri pulsanti IMPORTO / ESERCENTE.
    val customToolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = if (showToolbar) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

            override fun hide() {
                showToolbar = false
            }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                showToolbar = true
            }
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides customToolbar) {
        Box(modifier = modifier) {

            // ── Toolbar floating ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = showToolbar && currentSelection.length > 0,
                enter   = slideInVertically { -it } + fadeIn(),
                exit    = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1f).padding(top = 4.dp)
            ) {
                Card(
                    shape     = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier  = Modifier.padding(4.dp)
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Pulsante IMPORTO
                        AssistChip(
                            onClick = {
                                showToolbar = false
                                val s = minOf(currentSelection.start, currentSelection.end)
                                val e = maxOf(currentSelection.start, currentSelection.end)
                                if (s < e) onLabelAssigned(s, e, "IMPORTO")
                            },
                            label = { Text("IMPORTO", style = MaterialTheme.typography.labelMedium) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = colorImporto.copy(alpha = 0.15f),
                                labelColor     = colorImporto
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled     = true,
                                borderColor = colorImporto.copy(alpha = 0.5f)
                            )
                        )
                        // Pulsante ESERCENTE
                        AssistChip(
                            onClick = {
                                showToolbar = false
                                val s = minOf(currentSelection.start, currentSelection.end)
                                val e = maxOf(currentSelection.start, currentSelection.end)
                                if (s < e) onLabelAssigned(s, e, "ESERCENTE")
                            },
                            label = { Text("ESERCENTE", style = MaterialTheme.typography.labelMedium) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = colorEsercente.copy(alpha = 0.15f),
                                labelColor     = colorEsercente
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled     = true,
                                borderColor = colorEsercente.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // ── Testo selezionabile ───────────────────────────────────────────
            BasicTextField(
                value    = TextFieldValue(
                    annotatedString = annotatedString,
                    selection       = currentSelection
                ),
                onValueChange = { tfv ->
                    // Aggiorna solo la selezione, non il testo (readOnly)
                    currentSelection = tfv.selection
                    // Se la selezione è vuota, nasconde la toolbar
                    if (tfv.selection.length == 0) showToolbar = false
                },
                readOnly  = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier  = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            )
        }
    }
}

/** Legenda dei colori per IMPORTO / ESERCENTE. */
@Composable
fun SelectionLegend(modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        LegendChip(color = colorImporto,   label = "IMPORTO")
        LegendChip(color = colorEsercente, label = "ESERCENTE")
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color = color.copy(alpha = 0.4f), shape = RoundedCornerShape(3.dp))
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

