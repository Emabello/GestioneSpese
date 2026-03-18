/**
 * WebankParser.kt
 *
 * Parser per le notifiche push dell'app Webank. Estrae importo, merchant e data
 * dal testo della notifica usando espressioni regolari.
 *
 * Supporta due formati di notifica:
 * 1. **ADDEBITO GENERICO CARTA** — formato recente con `Data:` e `Ora:` espliciti.
 * 2. **Autorizzato pagamento / Pagamento autorizzato** — formato precedente senza data.
 *
 * Se nessun pattern corrisponde, [parseWebank] restituisce `null` e il
 * [BankNotificationListener] ignora la notifica.
 */
package com.emanuele.gestionespese.notifications

import com.emanuele.gestionespese.BuildConfig

/**
 * Risultato del parsing di una notifica Webank.
 *
 * @property amountCents Importo della transazione in centesimi di euro.
 * @property merchant    Nome del merchant/esercente.
 * @property dateMillis  Timestamp Unix in millisecondi della transazione.
 */
data class WebankParsed(
    val amountCents: Long,
    val merchant: String,
    val dateMillis: Long
)

/**
 * Parsa il testo di una notifica Webank ed estrae i dati della transazione.
 *
 * Prova prima il Pattern 1 (ADDEBITO GENERICO CARTA), poi il Pattern 2
 * (Pagamento autorizzato). Restituisce `null` se nessuno corrisponde.
 *
 * @param text     Testo completo della notifica (title + text + bigText).
 * @param postTime Timestamp Unix di ricezione della notifica (fallback per la data).
 * @return [WebankParsed] con i dati estratti, o `null` se il parsing fallisce.
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
 * Formato atteso: `"Data: 17/03/2026 Ora: 03:15"`.
 *
 * @param text Testo della notifica.
 * @return Timestamp Unix in millisecondi, o `null` se la data non è trovata.
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