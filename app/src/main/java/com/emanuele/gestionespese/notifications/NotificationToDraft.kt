package com.emanuele.gestionespese.notifications

import java.security.MessageDigest
import java.util.Locale

data class ParsedTxn(
    val amountCents: Long?,
    val currency: String?,
    val merchant: String?
)

fun parseTransaction(title: String, text: String, big: String): ParsedTxn {
    val all = listOf(title, text, big).joinToString(" ").trim()

    // Importo tipo "9,00 €" oppure "€ 9.00"
    val regex1 = Regex("""(\d{1,6})([.,](\d{2}))?\s*€""")
    val regex2 = Regex("""€\s*(\d{1,6})([.,](\d{2}))?""")

    fun matchToCents(m: MatchResult): Long {
        val intPart = m.groupValues[1].toLong()
        val decPart = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toLong() ?: 0L
        return intPart * 100 + decPart
    }

    val m = regex1.find(all) ?: regex2.find(all)
    val cents = m?.let { matchToCents(it) }

    // Merchant: per ora grezzo -> se title sembra un nome, usiamolo
    val merchant = title
        .takeIf { it.isNotBlank() }
        ?.trim()
        ?.take(80)

    return ParsedTxn(
        amountCents = cents,
        currency = if (cents != null) "EUR" else null,
        merchant = merchant
    )
}

fun buildDedupKey(sourceLabel: String, merchant: String?, amountCents: Long?, timeMillis: Long): String {
    // bucket 2 minuti per evitare doppioni ravvicinati
    val bucket = timeMillis / (2 * 60 * 1000L)
    val raw = "${sourceLabel.lowercase(Locale.ROOT)}|${merchant.orEmpty().lowercase(Locale.ROOT)}|${amountCents ?: -1}|$bucket"
    val md = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return md.joinToString("") { "%02x".format(it) }
}