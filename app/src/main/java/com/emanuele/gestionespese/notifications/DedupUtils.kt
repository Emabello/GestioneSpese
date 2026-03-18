/**
 * DedupUtils.kt
 *
 * Utility per la deduplicazione delle notifiche bancarie. Genera una chiave
 * SHA-256 univoca per ogni transazione, raggruppando gli eventi in finestre
 * temporali di 2 minuti per tollerare notifiche duplicate ravvicinate
 * (es. più notifiche per lo stesso pagamento).
 */
package com.emanuele.gestionespese.notifications

import java.security.MessageDigest
import java.util.Locale

/**
 * Genera una chiave di deduplicazione SHA-256 per una transazione bancaria.
 *
 * La chiave è costruita concatenando sorgente, merchant, importo e la
 * finestra temporale di 2 minuti (`timeMillis / 120_000`). Due notifiche
 * identiche ricevute entro 2 minuti produrranno la stessa chiave e la seconda
 * sarà ignorata dal DAO con `INSERT OR IGNORE`.
 *
 * @param sourceLabel  Identificativo della sorgente (es. `"Webank"`).
 * @param merchant     Nome del merchant/esercente, o `null` se assente.
 * @param amountCents  Importo in centesimi, o `null` se non estratto.
 * @param timeMillis   Timestamp Unix in millisecondi della notifica.
 * @return Hash SHA-256 in formato esadecimale lowercase (64 caratteri).
 */
fun buildDedupKey(
    sourceLabel: String,
    merchant: String?,
    amountCents: Long?,
    timeMillis: Long
): String {
    val bucket = timeMillis / (2 * 60 * 1_000L)
    val raw = "${sourceLabel.lowercase(Locale.ROOT)}|${merchant.orEmpty().lowercase(Locale.ROOT)}|${amountCents ?: -1}|$bucket"
    return MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
