package com.emanuele.gestionespese.notifications

import com.emanuele.gestionespese.data.local.entities.ParseRuleEntity
import com.emanuele.gestionespese.utils.DevLogger

/**
 * Risultato del parsing generico di una notifica bancaria.
 *
 * @property amountCents Importo in centesimi di euro (es. 250 = 2,50 €).
 * @property merchant    Nome del merchant/esercente.
 * @property dateMillis  Timestamp Unix in millisecondi della transazione.
 */
data class ParsedNotification(
    val amountCents: Long,
    val merchant: String,
    val dateMillis: Long
)

/**
 * Parser generico per notifiche bancarie. Usa le [ParseRuleEntity] configurate dall'utente
 * per estrarre importo, merchant e data dal testo della notifica.
 *
 * Supporta tre formati per l'importo:
 * 1. 2 gruppi (euro + centesimi): `(\d+)[,.](\d{2})` → groups[1]=euro, groups[2]=centesimi
 * 2. 1 gruppo decimale: `(\d+[.,]\d{2})` → replace `,` con `.` → toDouble * 100
 * 3. Formato migliaia italiano: `"1.234,56"` → rimuovi punti, poi come caso 2
 *
 * Per DATE la regex deve avere 5 gruppi: (dd)(MM)(yyyy)(HH)(mm).
 */
object GenericBankParser {

    /**
     * Tenta il parsing del testo della notifica usando le regole fornite.
     *
     * @param text         Testo completo della notifica.
     * @param rules        Regole di parsing ordinate per priority ASC.
     * @param fallbackTime Timestamp da usare se la DATE non viene trovata (postTime SBN).
     * @param debug        Se true, logga ogni regex tentata su [DevLogger].
     * @return [ParsedNotification] se AMOUNT viene trovato, null altrimenti.
     */
    fun parse(
        text: String,
        rules: List<ParseRuleEntity>,
        fallbackTime: Long,
        debug: Boolean = false
    ): ParsedNotification? {
        val amountRules   = rules.filter { it.field == "AMOUNT" }
        val merchantRules = rules.filter { it.field == "MERCHANT" }
        val dateRules     = rules.filter { it.field == "DATE" }

        val amountCents = parseAmount(text, amountRules, debug) ?: run {
            if (debug) DevLogger.log("PARSER", "❌ Nessuna regex AMOUNT ha trovato un match")
            return null
        }
        val merchant    = parseMerchant(text, merchantRules, debug) ?: "Pagamento"
        val dateMillis  = parseDate(text, dateRules, debug) ?: fallbackTime

        if (debug) {
            DevLogger.log("PARSER", "✅ Parsing OK — amountCents=$amountCents merchant='$merchant'")
        }
        return ParsedNotification(amountCents, merchant, dateMillis)
    }

    // ── Importo ────────────────────────────────────────────────────────────────

    private fun parseAmount(
        text: String,
        rules: List<ParseRuleEntity>,
        debug: Boolean
    ): Long? {
        for (rule in rules) {
            val match = matchRule(text, rule, debug, "AMOUNT") ?: continue
            val groups = match.groupValues
            return try {
                when {
                    // Caso 1: 2 gruppi (euro + centesimi)
                    groups.size >= 3 && groups[2].isNotEmpty() -> {
                        val euro  = groups[1].replace(".", "").toLong()
                        val cents = groups[2].toLong()
                        val total = euro * 100 + cents
                        if (debug) DevLogger.log("PARSER", "AMOUNT (2-group) = $total cents")
                        total
                    }
                    // Caso 2 e 3: 1 gruppo decimale o con migliaia
                    else -> {
                        val raw = groups[rule.groupIndex]
                            .replace(".", "")   // rimuovi separatori migliaia
                            .replace(",", ".")  // normalizza decimale
                        val total = (raw.toDouble() * 100).toLong()
                        if (debug) DevLogger.log("PARSER", "AMOUNT (1-group) raw='$raw' = $total cents")
                        total
                    }
                }
            } catch (e: Exception) {
                if (debug) DevLogger.log("PARSER", "AMOUNT parse error: ${e.message}")
                null
            }
        }
        return null
    }

    // ── Merchant ──────────────────────────────────────────────────────────────

    private fun parseMerchant(
        text: String,
        rules: List<ParseRuleEntity>,
        debug: Boolean
    ): String? {
        for (rule in rules) {
            val match = matchRule(text, rule, debug, "MERCHANT") ?: continue
            val value = match.groupValues.getOrNull(rule.groupIndex)?.trim()
            if (!value.isNullOrBlank()) {
                if (debug) DevLogger.log("PARSER", "MERCHANT = '$value'")
                return value
            }
        }
        return null
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /**
     * Interpreta la DATE regex con 5 gruppi: (dd)(MM)(yyyy)(HH)(mm).
     */
    private fun parseDate(
        text: String,
        rules: List<ParseRuleEntity>,
        debug: Boolean
    ): Long? {
        for (rule in rules) {
            val match = matchRule(text, rule, debug, "DATE") ?: continue
            return try {
                val g = match.groupValues
                val cal = java.util.Calendar.getInstance().apply {
                    set(g[3].toInt(), g[2].toInt() - 1, g[1].toInt(), g[4].toInt(), g[5].toInt(), 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val millis = cal.timeInMillis
                if (debug) DevLogger.log("PARSER", "DATE = ${g[1]}/${g[2]}/${g[3]} ${g[4]}:${g[5]} → $millis")
                millis
            } catch (e: Exception) {
                if (debug) DevLogger.log("PARSER", "DATE parse error: ${e.message}")
                null
            }
        }
        return null
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun matchRule(
        text: String,
        rule: ParseRuleEntity,
        debug: Boolean,
        fieldLabel: String
    ): MatchResult? {
        return try {
            val regex = Regex(rule.regex, RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            if (debug) {
                val preview = text.take(80).replace("\n", "↵")
                if (match != null) {
                    DevLogger.log("PARSER", "$fieldLabel ✓ regex='${rule.regex.take(60)}' groups=${match.groupValues.drop(1).take(3)}")
                } else {
                    DevLogger.log("PARSER", "$fieldLabel ✗ regex='${rule.regex.take(60)}' text='$preview'")
                }
            }
            match
        } catch (e: Exception) {
            if (debug) DevLogger.log("PARSER", "$fieldLabel regex error: ${e.message}")
            null
        }
    }
}
