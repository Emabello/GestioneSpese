package com.emanuele.gestionespese.data.model

import java.util.UUID

enum class WidgetType {
    TOTALE_USCITE,
    TOTALE_ENTRATE,
    SALDO_MESE,
    GRAFICO_TORTA,
    ULTIMI_MOVIMENTI,
    TOP_CATEGORIE
}

enum class WidgetSize {
    SMALL,  // mezza larghezza
    WIDE    // larghezza intera
}

enum class WidgetPeriodo {
    MESE_CORRENTE,
    ULTIMI_30_GIORNI,
    ANNO_CORRENTE
}

data class WidgetConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: WidgetType,
    val size: WidgetSize = WidgetSize.WIDE,
    val position: Int = 0,
    val periodo: WidgetPeriodo = WidgetPeriodo.MESE_CORRENTE,
    val topN: Int = 5           // per TOP_CATEGORIE e ULTIMI_MOVIMENTI
)

// Layout default se l'utente non ha ancora configurato nulla
fun defaultDashboardLayout(): List<WidgetConfig> = listOf(
    WidgetConfig(type = WidgetType.SALDO_MESE,         size = WidgetSize.WIDE,  position = 0),
    WidgetConfig(type = WidgetType.TOTALE_USCITE,      size = WidgetSize.SMALL, position = 1),
    WidgetConfig(type = WidgetType.TOTALE_ENTRATE,     size = WidgetSize.SMALL, position = 2),
    WidgetConfig(type = WidgetType.GRAFICO_TORTA,      size = WidgetSize.WIDE,  position = 3),
    WidgetConfig(type = WidgetType.TOP_CATEGORIE,      size = WidgetSize.WIDE,  position = 4),
    WidgetConfig(type = WidgetType.ULTIMI_MOVIMENTI,   size = WidgetSize.WIDE,  position = 5)
)