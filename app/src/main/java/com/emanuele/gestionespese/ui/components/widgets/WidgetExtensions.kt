/**
 * WidgetExtensions.kt
 *
 * Extension functions su [SpesaView], [List<SpesaView>] e [WidgetPeriodo] usate
 * dai widget della dashboard per filtrare e classificare i movimenti.
 *
 * - [filteredByPeriodo]: filtra la lista in base al periodo temporale selezionato
 * - [isEntrata] / [isUscita]: classificazione del tipo di movimento
 * - [WidgetPeriodo.label]: etichetta breve per la UI
 */
package com.emanuele.gestionespese.ui.components.widgets

import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetPeriodo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Prova a parsare una stringa data nei formati `"yyyy-MM-dd"` e `"dd/MM/yyyy"`.
 *
 * @param data Stringa data da parsare, o `null`.
 * @return [LocalDate] parsato, o `null` se la stringa non è riconoscibile.
 */
private fun parseDataFlessibile(data: String?): LocalDate? {
    if (data.isNullOrBlank()) return null
    return try {
        LocalDate.parse(data, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    } catch (e: Exception) {
        try {
            LocalDate.parse(data, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e2: Exception) {
            null
        }
    }
}

/**
 * Filtra la lista di movimenti in base al [periodo] selezionato.
 *
 * @param periodo Intervallo temporale ([WidgetPeriodo]).
 * @return Sottolista dei movimenti nel periodo indicato.
 */
fun List<SpesaView>.filteredByPeriodo(periodo: WidgetPeriodo): List<SpesaView> {
    val today = LocalDate.now()
    return when (periodo) {
        WidgetPeriodo.MESE_CORRENTE -> filter { spesa ->
            val d = parseDataFlessibile(spesa.data) ?: return@filter false
            d.year == today.year && d.monthValue == today.monthValue
        }
        WidgetPeriodo.ULTIMI_30_GIORNI -> {
            val from = today.minusDays(30)
            filter { spesa ->
                val d = parseDataFlessibile(spesa.data) ?: return@filter false
                !d.isBefore(from)
            }
        }
        WidgetPeriodo.ANNO_CORRENTE -> filter { spesa ->
            val d = parseDataFlessibile(spesa.data) ?: return@filter false
            d.year == today.year
        }
    }
}

/** `true` se il movimento è un trasferimento tra conti. */
fun SpesaView.isTransfer(): Boolean =
    tipo_movimento?.equals("trasferimento", ignoreCase = true) == true

/** `true` se il movimento è un'entrata (in base a `tipo_movimento`). */
fun SpesaView.isEntrata(): Boolean =
    tipo_movimento?.equals("entrata", ignoreCase = true) == true

/**
 * `true` se il movimento è un'uscita. Usa `tipo_movimento` se disponibile;
 * altrimenti inferisce dal campo `tipo` escludendo le entrate.
 */
fun SpesaView.isUscita(): Boolean {
    if (tipo_movimento != null) {
        return tipo_movimento.equals("uscita", ignoreCase = true)
    }
    return tipo?.contains("reddito", ignoreCase = true) != true &&
            tipo?.contains("entrata", ignoreCase = true) != true
}

/**
 * Calcola il saldo cumulativo (tutto il tempo) per un singolo conto.
 * I trasferimenti in uscita dal conto riducono il saldo; quelli in entrata lo aumentano.
 * I trasferimenti NON incidono sul saldo globale ma influenzano i singoli conti.
 */
fun List<SpesaView>.saldoPerConto(conto: String, initialBalance: Double = 0.0): Double {
    val entrate             = filter { it.conto == conto && it.isEntrata() }.sumOf { it.importo }
    val uscite              = filter { it.conto == conto && it.isUscita() }.sumOf { it.importo }
    val trasfUsciti         = filter { it.conto == conto && it.isTransfer() }.sumOf { it.importo }
    val trasfEntratiInConto = filter { it.conto_destinazione == conto && it.isTransfer() }.sumOf { it.importo }
    return initialBalance + entrate - uscite - trasfUsciti + trasfEntratiInConto
}

/** Restituisce l'etichetta breve del periodo per la UI (es. `"mese"`, `"30gg"`, `"anno"`). */
fun WidgetPeriodo.label(): String = when (this) {
    WidgetPeriodo.MESE_CORRENTE    -> "mese"
    WidgetPeriodo.ULTIMI_30_GIORNI -> "30gg"
    WidgetPeriodo.ANNO_CORRENTE    -> "anno"
}