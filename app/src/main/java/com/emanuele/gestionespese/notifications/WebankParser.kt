package com.emanuele.gestionespese.notifications

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
    if (!text.contains("Autorizzato pagamento")) return null

    val amountMatch = Regex("""(\d+),(\d{2})\s*Euro""").find(text) ?: return null
    val amountCents = amountMatch.groupValues[1].toLong() * 100 + amountMatch.groupValues[2].toLong()

    val merchant = Regex("""Euro\s*-\s*(.*?)\s*con\s*Carta""")
        .find(text)?.groupValues?.get(1)?.trim() ?: "Pagamento carta"

    return WebankParsed(amountCents = amountCents, merchant = merchant, dateMillis = postTime)
}