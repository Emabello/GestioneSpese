package com.emanuele.gestionespese.notifications

data class WebankParsed(
    val amountCents: Long,
    val merchant: String,
    val dateMillis: Long
)

fun parseWebank(text: String, postTime: Long): WebankParsed? {

    if (!text.contains("Autorizzato pagamento")) return null

    // IMPORTO
    val amountRegex = Regex("""(\d+),(\d{2})\s*Euro""")
    val amountMatch = amountRegex.find(text) ?: return null

    val euro = amountMatch.groupValues[1].toLong()
    val cent = amountMatch.groupValues[2].toLong()
    val amountCents = euro * 100 + cent

    // MERCHANT
    val merchantRegex = Regex("""Euro\s*-\s*(.*?)\s*con\s*Carta""")
    val merchantMatch = merchantRegex.find(text)
    val merchant = merchantMatch?.groupValues?.get(1)?.trim() ?: "Pagamento carta"

    return WebankParsed(
        amountCents = amountCents,
        merchant = merchant,
        dateMillis = postTime
    )
}