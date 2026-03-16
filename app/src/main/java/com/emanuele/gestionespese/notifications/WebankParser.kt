package com.emanuele.gestionespese.notifications

import com.emanuele.gestionespese.BuildConfig

data class WebankParsed(
    val amountCents: Long,
    val merchant: String,
    val dateMillis: Long
)

/**
 * Parsa il testo di una notifica Webank e ritorna i dati della transazione,
 * oppure null se il pattern non corrisponde.
 */
fun parseWebank(text: String, postTime: Long): WebankParsed? {
    // Accetta varianti: "Autorizzato pagamento", "Pagamento autorizzato", "pagamento di"
    val isPayment = text.contains("Autorizzato pagamento", ignoreCase = true) ||
            text.contains("Pagamento autorizzato", ignoreCase = true) ||
            text.contains("pagamento di", ignoreCase = true) ||
            text.contains("Acquisto", ignoreCase = true)

    if (!isPayment) return null

    // Importo: accetta "12,34 Euro", "12.34 EUR", "€ 12,34", "12,34€"
    val amountMatch = Regex(
        """(?:€\s*)?(\d+)[,.](\d{2})\s*(?:Euro|EUR|€)?""",
        RegexOption.IGNORE_CASE
    ).find(text) ?: return null

    val amountCents = amountMatch.groupValues[1].toLong() * 100 +
            amountMatch.groupValues[2].toLong()

    // Merchant: prova vari pattern
    val merchant = Regex("""Euro\s*[-–]\s*(.*?)\s*con\s*Carta""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.trim()
        ?: Regex("""EUR\s*[-–]\s*(.*?)\s*con\s*Carta""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
        ?: Regex("""presso\s+(.*?)(?:\s*\.|$)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
        ?: Regex("""da\s+(.*?)\s+(?:di|il|con)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
        ?: "Pagamento carta"

    if (BuildConfig.DEBUG) {
        android.util.Log.d("WebankParser",
            "Parsed: amountCents=$amountCents merchant='$merchant'")
    }

    return WebankParsed(
        amountCents = amountCents,
        merchant    = merchant,
        dateMillis  = postTime
    )
}