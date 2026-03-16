package com.emanuele.gestionespese.ui.components.widgets

import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetPeriodo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

fun SpesaView.isEntrata(): Boolean =
    tipo_movimento?.equals("entrata", ignoreCase = true) == true

fun SpesaView.isUscita(): Boolean {
    // Se tipo_movimento è presente usalo
    if (tipo_movimento != null) {
        return tipo_movimento.equals("uscita", ignoreCase = true)
    }
    return tipo?.contains("reddito", ignoreCase = true) != true &&
            tipo?.contains("entrata", ignoreCase = true) != true
}

fun WidgetPeriodo.label(): String = when (this) {
    WidgetPeriodo.MESE_CORRENTE    -> "mese"
    WidgetPeriodo.ULTIMI_30_GIORNI -> "30gg"
    WidgetPeriodo.ANNO_CORRENTE    -> "anno"
}