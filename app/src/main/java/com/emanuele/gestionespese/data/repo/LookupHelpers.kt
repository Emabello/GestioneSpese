/**
 * LookupHelpers.kt
 *
 * Contiene:
 * - [LookupsLocal]: aggregazione delle lookup tables usata dai ViewModel.
 * - Funzioni di utilità per il parsing e la conversione dei dati ricevuti
 *   dal backend (mappe JSON) nelle entità Room o nelle stringhe usate dalla UI.
 *
 * Tutte le funzioni sono `internal` e usate esclusivamente da [SpeseRepository].
 */
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

/**
 * Cerca il primo valore non-blank tra le chiavi indicate in una mappa JSON.
 *
 * @param keys Chiavi da cercare in ordine di priorità.
 * @return Primo valore non-blank trovato, o `null` se nessuno è valido.
 */
internal fun Map<String, Any?>.firstNonBlank(vararg keys: String): String? {
    for (k in keys) {
        val s = this[k]?.toString()?.trim()
        if (!s.isNullOrBlank()) return s
    }
    return null
}

/**
 * Legge il campo di attivazione (`attivo`/`attiva`/`active`) dalla mappa.
 * Restituisce `true` come default se il campo è assente (comportamento "opt-out").
 *
 * @return `true` se il record è attivo, `false` se esplicitamente disabilitato.
 */
internal fun Map<String, Any?>.isActiveDefaultTrue(): Boolean {
    val raw = this["attivo"] ?: this["ATTIVO"] ?: this["attiva"] ?: this["ATTIVA"] ?: this["active"]
    return raw.asBoolDefaultTrue()
}

/**
 * Converte qualsiasi valore ricevuto dal backend in `Boolean`.
 * Restituisce `true` come default se il valore è `null`.
 *
 * @return `true` per `null`, `true`, `"true"`, `"1"`, `"yes"`, numero != 0; `false` altrimenti.
 */
internal fun Any?.asBoolDefaultTrue(): Boolean = when (this) {
    null -> true
    is Boolean -> this
    is String -> this.equals("true", ignoreCase = true) || this == "1" || this.equals("yes", ignoreCase = true)
    is Number -> this.toInt() != 0
    else -> true
}

/**
 * Converte un numero decimale in stringa, rimuovendo il `.0` finale se intero.
 * Es: `"3.0"` → `"3"`, `"4.5"` → `"4.5"`.
 *
 * @return Stringa numerica pulita, o la stringa originale se non è un numero valido.
 */
internal fun String.numToCleanString(): String {
    val d = this.trim().toDoubleOrNull() ?: return this.trim()
    return if (d % 1.0 == 0.0) d.toInt().toString() else this.trim()
}

/**
 * Costruisce una label leggibile combinando ID e descrizione.
 * - Se entrambi presenti: `"id - descrizione"`
 * - Se solo uno: restituisce quello non-blank
 * - Se entrambi blank: restituisce `null`
 *
 * @param id    Identificativo del record (es. codice conto).
 * @param descr Descrizione testuale del record.
 * @return Label formattata, o `null` se entrambi i campi sono vuoti.
 */
internal fun buildLabel(id: String?, descr: String?): String? {
    val i = id?.trim().orEmpty()
    val d = descr?.trim().orEmpty()
    if (i.isBlank() && d.isBlank()) return null
    return if (i.isNotBlank() && d.isNotBlank()) "$i - $d" else d.ifBlank { i }
}