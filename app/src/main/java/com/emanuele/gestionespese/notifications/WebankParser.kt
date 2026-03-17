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

    // ── Pattern 1: ADDEBITO GENERICO CARTA ──────────────────────────────────
    // Formato: "ADDEBITO GENERICO CARTA*2058 VISA DIGIT-00:00-MILAN HOLIDAY S.A di -2,50 EURO sul conto *2465 Data: 17/03/2026 Ora: 03:15"
    val addebitoMatch = Regex(
        """ADDEBITO GENERICO CARTA\*\d+\s+\S+\s+DIGIT-\d+:\d+-(.+?)\s+di\s+-?(\d+)[,.](\d{2})\s*EURO""",
        RegexOption.IGNORE_CASE
    ).find(text)

    if (addebitoMatch != null) {
        val merchant    = addebitoMatch.groupValues[1].trim()
        val amountCents = addebitoMatch.groupValues[2].toLong() * 100 +
                addebitoMatch.groupValues[3].toLong()

        // Data dal testo se presente, altrimenti postTime
        val dateMillis = parseDateFromText(text) ?: postTime

        if (BuildConfig.DEBUG) {
            android.util.Log.d("WebankParser",
                "[ADDEBITO] merchant='$merchant' amountCents=$amountCents")
        }

        return WebankParsed(amountCents = amountCents, merchant = merchant, dateMillis = dateMillis)
    }

    // ── Pattern 2: Pagamento carta (formato precedente) ──────────────────────
    val isPayment = text.contains("Autorizzato pagamento", ignoreCase = true) ||
            text.contains("Pagamento autorizzato", ignoreCase = true) ||
            text.contains("pagamento di", ignoreCase = true) ||
            text.contains("Acquisto", ignoreCase = true)

    if (!isPayment) return null

    val amountMatch = Regex(
        """(?:€\s*)?(\d+)[,.](\d{2})\s*(?:Euro|EUR|€)?""",
        RegexOption.IGNORE_CASE
    ).find(text) ?: return null

    val amountCents = amountMatch.groupValues[1].toLong() * 100 +
            amountMatch.groupValues[2].toLong()

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

/**
 * Prova a estrarre la data dal testo della notifica.
 * Formato atteso: "Data: 17/03/2026 Ora: 03:15"
 */
private fun parseDateFromText(text: String): Long? {
    val match = Regex("""Data:\s*(\d{2})/(\d{2})/(\d{4})\s+Ora:\s*(\d{2}):(\d{2})""")
        .find(text) ?: return null

    return try {
        val (day, month, year, hour, minute) = match.destructured
        val cal = java.util.Calendar.getInstance().apply {
            set(year.toInt(), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    } catch (e: Exception) {
        null
    }
}