package com.emanuele.gestionespese.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.utils.WizardSelection

// Colori per le etichette nel testo
private val colorImporto   = Color(0xFF3949AB) // Indigo
private val colorEsercente = Color(0xFF388E3C) // Verde
private val colorPending   = Color(0xFFF9A825) // Ambra — selezione non ancora confermata

/**
 * Composable che mostra [text] con selezione a tocco.
 *
 * UX:
 * - **Primo tocco**: la parola toccata viene evidenziata in giallo (anchor).
 * - **Secondo tocco su parola diversa**: il range tra anchor e focus viene evidenziato.
 *   Appare il pannello con i pulsanti IMPORTO e ESERCENTE.
 * - **Secondo tocco sulla stessa parola**: conferma la singola parola come selezione.
 * - **Pulsante ✕**: azzera la selezione pending.
 * - Le selezioni già salvate restano evidenziate con i colori di etichetta (blu/verde).
 *
 * @param text             Testo completo della notifica.
 * @param selections       Selezioni già etichettate e salvate.
 * @param onLabelAssigned  Callback: (start, end, label) quando l'utente preme IMPORTO/ESERCENTE.
 */
@Composable
fun SelectableNotificationText(
    text: String,
    selections: List<WizardSelection>,
    onLabelAssigned: (start: Int, end: Int, label: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stato selezione pending (non ancora confermata con etichetta)
    var anchorOffset by remember { mutableStateOf<Int?>(null) }
    var focusOffset  by remember { mutableStateOf<Int?>(null) }

    // Risultato layout testo — serve per convertire posizione tap → offset carattere
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Range pending normalizzato (word-snapped), INCLUSIVO su entrambi gli estremi.
    // snapWordEnd restituisce l'indice ESCLUSO → usiamo snapWordEnd - 1 per l'estremo destro.
    val pendingRange: IntRange? = remember(anchorOffset, focusOffset, text) {
        val a = anchorOffset ?: return@remember null
        val wordStart = snapWordStart(text, a)
        val wordEnd   = (snapWordEnd(text, a) - 1).coerceAtLeast(wordStart)
        val f = focusOffset
            ?: return@remember IntRange(wordStart, wordEnd)
        val s = minOf(a, f)
        val e = maxOf(a, f)
        IntRange(snapWordStart(text, s), (snapWordEnd(text, e) - 1).coerceAtLeast(snapWordStart(text, s)))
    }

    // AnnotatedString con evidenziazioni
    val annotatedString = remember(text, selections, pendingRange) {
        buildAnnotatedString {
            // Costruisce una mappa di colori per ogni carattere:
            // selezioni salvate hanno precedenza sul pending
            val bgColors = Array<Color?>(text.length) { null }
            val fgColors = Array<Color?>(text.length) { null }

            // Pending (ambra) — più bassa priorità
            pendingRange?.forEach { i ->
                if (i < text.length) {
                    bgColors[i] = colorPending.copy(alpha = 0.30f)
                    fgColors[i] = Color(0xFF5D4037) // marrone scuro
                }
            }

            // Selezioni salvate — sovrascrivono il pending
            // sel.end è ESCLUSO, quindi iteriamo s until e
            for (sel in selections) {
                val s = sel.start.coerceIn(0, text.length)
                val e = sel.end.coerceIn(s, text.length)
                val bg = when (sel.label) {
                    "IMPORTO"   -> colorImporto.copy(alpha = 0.25f)
                    "ESERCENTE" -> colorEsercente.copy(alpha = 0.25f)
                    else        -> Color.Transparent
                }
                val fg = when (sel.label) {
                    "IMPORTO"   -> colorImporto
                    "ESERCENTE" -> colorEsercente
                    else        -> Color.Unspecified
                }
                for (i in s until e) {  // until = esclude e
                    bgColors[i] = bg
                    fgColors[i] = fg
                }
            }

            // Rendering: raggruppa caratteri contigui con lo stesso stile
            var i = 0
            while (i < text.length) {
                val bg = bgColors[i]
                val fg = fgColors[i]
                // Trova la fine del tratto con lo stesso stile
                var j = i + 1
                while (j < text.length && bgColors[j] == bg && fgColors[j] == fg) j++

                if (bg != null || fg != null) {
                    withStyle(
                        SpanStyle(
                            background = bg ?: Color.Transparent,
                            color      = fg ?: Color.Unspecified,
                            fontWeight = if (fg != null) FontWeight.Medium else FontWeight.Normal
                        )
                    ) { append(text.substring(i, j)) }
                } else {
                    append(text.substring(i, j))
                }
                i = j
            }
        }
    }

    Column(modifier = modifier) {
        // ── Testo toccabile ──────────────────────────────────────────────────
        Text(
            text     = annotatedString,
            style    = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
                .pointerInput(text) {
                    detectTapGestures { offset ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val charOffset = layout.getOffsetForPosition(offset)

                        when {
                            // Nessuna selezione → imposta anchor
                            anchorOffset == null -> {
                                anchorOffset = charOffset
                                focusOffset  = null
                            }
                            // Anchor già impostato → imposta focus (o resetta se tocco vicino)
                            else -> {
                                focusOffset = charOffset
                            }
                        }
                    }
                },
            onTextLayout = { textLayoutResult = it }
        )

        // ── Pannello azioni (appare solo con selezione pending) ───────────────
        val hasSelection = pendingRange != null && !pendingRange.isEmpty()

        if (hasSelection) {
            Spacer(Modifier.height(8.dp))

            Surface(
                color  = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape  = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Testo selezionato (anteprima)
                    // r.last è inclusivo → r.last + 1 è l'indice esclusivo per substring
                    val selectedPreview = pendingRange?.let { r ->
                        val s = r.first.coerceIn(0, text.length)
                        val e = (r.last + 1).coerceIn(s, text.length)
                        text.substring(s, e).trim().take(40)
                    } ?: ""

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "\"$selectedPreview\"",
                            style     = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color     = colorPending,
                            modifier  = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick  = { anchorOffset = null; focusOffset = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, "Annulla selezione",
                                modifier = Modifier.size(16.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text(
                        "Etichetta questa parte come:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // IMPORTO
                        Button(
                            onClick = {
                                pendingRange?.let { r ->
                                    val s = r.first.coerceIn(0, text.length)
                                    val e = (r.last + 1).coerceIn(s, text.length)
                                    onLabelAssigned(s, e, "IMPORTO")
                                }
                                anchorOffset = null
                                focusOffset  = null
                            },
                            colors   = ButtonDefaults.buttonColors(containerColor = colorImporto),
                            shape    = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("IMPORTO", style = MaterialTheme.typography.labelMedium)
                        }

                        // ESERCENTE
                        Button(
                            onClick = {
                                pendingRange?.let { r ->
                                    val s = r.first.coerceIn(0, text.length)
                                    val e = (r.last + 1).coerceIn(s, text.length)
                                    onLabelAssigned(s, e, "ESERCENTE")
                                }
                                anchorOffset = null
                                focusOffset  = null
                            },
                            colors   = ButtonDefaults.buttonColors(containerColor = colorEsercente),
                            shape    = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ESERCENTE", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
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

// ── Word snapping ─────────────────────────────────────────────────────────────

/** Ritorna l'indice di inizio della parola che contiene [offset]. */
private fun snapWordStart(text: String, offset: Int): Int {
    val o = offset.coerceIn(0, text.length)
    var i = o
    while (i > 0 && !text[i - 1].isWordBoundary()) i--
    return i
}

/** Ritorna l'indice di fine (escluso) della parola che contiene [offset]. */
private fun snapWordEnd(text: String, offset: Int): Int {
    val o = offset.coerceIn(0, text.length)
    var i = o
    while (i < text.length && !text[i].isWordBoundary()) i++
    return i
}

/** Caratteri considerati separatori di parola per il word-snapping. */
private fun Char.isWordBoundary(): Boolean =
    this == ' ' || this == '\n' || this == '\t'
