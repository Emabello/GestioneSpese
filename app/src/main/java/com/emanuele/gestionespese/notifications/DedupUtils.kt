package com.emanuele.gestionespese.notifications

import java.security.MessageDigest
import java.util.Locale

/**
 * Genera una chiave deduplicazione SHA-256 basata su sorgente, merchant, importo
 * e finestra temporale di 2 minuti (evita duplicati per notifiche ravvicinate).
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