package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.model.SottoCatItem

/**
 * Aggregazione di tutte le lookup tables caricate dal database locale.
 * Usata da [SpeseRepository.getLookupsFromDb] per restituire in un'unica
 * struttura i dati necessari ai form dell'app.
 *
 * @property tipi           Lista dei tipi di spesa (es. "Uscita", "Entrata").
 * @property categorie      Lista delle categorie disponibili.
 * @property sottocategorie Lista di [SottoCatItem] con categoria e sottocategoria.
 * @property conti          Lista dei conti correnti dell'utente.
 */
data class LookupsLocal(
    val tipi: List<String>,
    val categorie: List<String>,
    val sottocategorie: List<SottoCatItem>,
    val conti: List<String>
)

// Cerca il primo valore non-blank tra le chiavi indicate
internal fun Map<String, Any?>.firstNonBlank(vararg keys: String): String? {
    for (k in keys) {
        val s = this[k]?.toString()?.trim()
        if (!s.isNullOrBlank()) return s
    }
    return null
}

// Controlla il campo "attivo/attiva/active" — default true se assente
internal fun Map<String, Any?>.isActiveDefaultTrue(): Boolean {
    val raw = this["attivo"] ?: this["ATTIVO"] ?: this["attiva"] ?: this["ATTIVA"] ?: this["active"]
    return raw.asBoolDefaultTrue()
}

// Converte qualsiasi valore in Boolean, default true se null
internal fun Any?.asBoolDefaultTrue(): Boolean = when (this) {
    null -> true
    is Boolean -> this
    is String -> this.equals("true", ignoreCase = true) || this == "1" || this.equals("yes", ignoreCase = true)
    is Number -> this.toInt() != 0
    else -> true
}

// "3.0" → "3", "4.5" → "4.5"
internal fun String.numToCleanString(): String {
    val d = this.trim().toDoubleOrNull() ?: return this.trim()
    return if (d % 1.0 == 0.0) d.toInt().toString() else this.trim()
}

// Costruisce label "id - descrizione", o solo uno dei due se l'altro è vuoto
internal fun buildLabel(id: String?, descr: String?): String? {
    val i = id?.trim().orEmpty()
    val d = descr?.trim().orEmpty()
    if (i.isBlank() && d.isBlank()) return null
    return if (i.isNotBlank() && d.isNotBlank()) "$i - $d" else d.ifBlank { i }
}