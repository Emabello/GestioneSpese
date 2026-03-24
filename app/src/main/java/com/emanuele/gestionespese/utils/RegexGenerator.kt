package com.emanuele.gestionespese.utils

import com.emanuele.gestionespese.data.local.entities.ParseRuleEntity

/**
 * Selezione effettuata dall'utente nel wizard di configurazione.
 *
 * @property start  Indice inizio selezione nel testo (incluso).
 * @property end    Indice fine selezione nel testo (escluso).
 * @property label  Etichetta assegnata: "IMPORTO" o "ESERCENTE".
 */
data class WizardSelection(
    val start: Int,
    val end: Int,
    val label: String
)

/**
 * Genera [ParseRuleEntity] a partire da selezioni manuali dell'utente sul testo della notifica.
 *
 * Strategia:
 * - Prende la parola immediatamente a sinistra e a destra della selezione come "ancoraggi".
 * - Per IMPORTO: rileva automaticamente il formato numerico (decimale o migliaia italiane).
 * - Per ESERCENTE: usa il pattern `(.+?)` tra gli ancoraggi.
 */
object RegexGenerator {

    /**
     * Genera la lista di [ParseRuleEntity] (senza bankProfileId) dalle selezioni del wizard.
     *
     * @param text       Testo completo della notifica di esempio.
     * @param selections Lista di selezioni etichettate dall'utente.
     * @return Lista di regole pronte da salvare (bankProfileId = 0, verrà impostato al salvataggio).
     */
    fun generateRules(text: String, selections: List<WizardSelection>): List<ParseRuleEntity> {
        return selections.mapIndexed { index, sel ->
            val fieldName = when (sel.label) {
                "IMPORTO"   -> "AMOUNT"
                "ESERCENTE" -> "MERCHANT"
                else        -> sel.label
            }
            val regex = buildRegex(text, sel)
            ParseRuleEntity(
                bankProfileId = 0,
                field         = fieldName,
                regex         = regex,
                groupIndex    = 1,
                priority      = index,
                description   = "Generato dal wizard"
            )
        }
    }

    /**
     * Costruisce la stringa regex per una singola selezione.
     * Usa la parola immediatamente precedente e successiva come ancoraggi.
     */
    fun buildRegex(text: String, sel: WizardSelection): String {
        val start = sel.start.coerceIn(0, text.length)
        val end   = sel.end.coerceIn(start, text.length)

        val leftContext  = text.substring(0, start)
        val rightContext = text.substring(end)

        val leftWord  = extractLastWord(leftContext)
        val rightWord = extractFirstWord(rightContext)

        val leftAnchor  = if (leftWord.isNotEmpty())  Regex.escape(leftWord)  + "\\s*" else ""
        val rightAnchor = if (rightWord.isNotEmpty()) "\\s*" + Regex.escape(rightWord) else ""

        val captureGroup = when (sel.label) {
            "IMPORTO"   -> detectAmountPattern(text.substring(start, end))
            else        -> "(.+?)"
        }

        return "$leftAnchor$captureGroup$rightAnchor"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Estrae l'ultima parola (token non-spazio) da una stringa. */
    private fun extractLastWord(s: String): String {
        val trimmed = s.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        return if (lastSpace < 0) trimmed else trimmed.substring(lastSpace + 1)
    }

    /** Estrae la prima parola (token non-spazio) da una stringa. */
    private fun extractFirstWord(s: String): String {
        val trimmed = s.trimStart()
        val firstSpace = trimmed.indexOf(' ')
        return if (firstSpace < 0) trimmed else trimmed.substring(0, firstSpace)
    }

    /**
     * Rileva il pattern regex corretto per il valore numerico selezionato.
     *
     * Gestisce un eventuale simbolo di valuta (€, $, £…) davanti al numero
     * aggiungendo un prefisso opzionale `(?:[€$£¥₹]\s*)?` nel pattern generato.
     *
     * - "€24,90"    → `(?:[€$£¥₹]\s*)?(\d+[,.]\d{2})`
     * - "€1.234,56" → `(?:[€$£¥₹]\s*)?(\d{1,3}(?:\.\d{3})*,\d{2})`
     * - "24,90"     → `(\d+[,.]\d{2})`
     * - "2.50"      → `(\d+[,.]\d{2})`
     */
    private fun detectAmountPattern(selected: String): String {
        val stripped  = selected.trim()
        val noSymbol  = stripped.trimStart { it in "€\$£¥₹" }.trim()
        val symbolPfx = if (noSymbol.length < stripped.length) "(?:[€\$£¥₹]\\s*)?" else ""

        // Migliaia italiane: ha il punto come separatore migliaia E la virgola come decimale
        // Es: "1.234,56" oppure "1.234.567,89"
        return if (noSymbol.contains('.') && noSymbol.contains(',') &&
                   noSymbol.indexOf('.') < noSymbol.indexOf(',')) {
            "$symbolPfx(\\d{1,3}(?:\\.\\d{3})*,\\d{2})"
        } else {
            "$symbolPfx(\\d+[,.]\\d{2})"
        }
    }
}
