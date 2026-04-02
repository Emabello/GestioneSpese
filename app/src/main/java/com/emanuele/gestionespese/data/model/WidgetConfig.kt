/**
 * WidgetConfig.kt
 *
 * Modelli di configurazione per i widget della dashboard personalizzabile.
 * Definisce i tipi di widget disponibili, le dimensioni (colSpan / heightStep),
 * i periodi temporali e il layout default applicato ai nuovi utenti.
 */
package com.emanuele.gestionespese.data.model

import java.util.UUID

/** Identifica il tipo di contenuto visualizzato dal widget. */
enum class WidgetType {
    TOTALE_USCITE,
    TOTALE_ENTRATE,
    SALDO_MESE,
    GRAFICO_TORTA,
    ULTIMI_MOVIMENTI,
    TOP_CATEGORIE,
    SALDO_CONTO,
    ANDAMENTO_MENSILE
}

/**
 * Dimensione legacy del widget (usata solo per migrazione JSON vecchi).
 * Il sistema attuale usa [WidgetConfig.colSpan].
 */
enum class WidgetSize {
    SMALL,
    WIDE
}

/** Step di altezza del widget: controlla quanti dettagli mostrare. */
enum class WidgetHeightStep {
    /** Solo metrica principale. */
    S,
    /** Metrica + elemento secondario (confronto %, entrate/uscite). */
    M,
    /** Metrica + dettagli completi (breakdown categorie, ecc.). */
    L
}

/** Intervallo temporale su cui il widget calcola i dati. */
enum class WidgetPeriodo {
    MESE_CORRENTE,
    ULTIMI_30_GIORNI,
    ANNO_CORRENTE
}

/** Valori validi per colSpan nella griglia a 6 colonne. */
val VALID_COL_SPANS = listOf(2, 3, 4, 6)

/** Numero di colonne di default per il tipo di widget. */
fun WidgetType.defaultColSpan(): Int = when (this) {
    WidgetType.TOTALE_USCITE,
    WidgetType.TOTALE_ENTRATE,
    WidgetType.SALDO_CONTO -> 3
    else -> 6
}

/** Numero minimo di colonne consentito per il tipo di widget. */
fun WidgetType.minColSpan(): Int = when (this) {
    WidgetType.GRAFICO_TORTA,
    WidgetType.ULTIMI_MOVIMENTI,
    WidgetType.TOP_CATEGORIE,
    WidgetType.ANDAMENTO_MENSILE -> 4
    else -> 2
}

/** HeightStep di default per il tipo di widget. */
fun WidgetType.defaultHeightStep(): WidgetHeightStep = when (this) {
    WidgetType.GRAFICO_TORTA,
    WidgetType.ULTIMI_MOVIMENTI,
    WidgetType.TOP_CATEGORIE,
    WidgetType.ANDAMENTO_MENSILE -> WidgetHeightStep.M
    else -> WidgetHeightStep.S
}

/**
 * Configurazione di un singolo widget nella dashboard.
 *
 * @property id          Identificativo UUID univoco del widget.
 * @property type        Tipo di contenuto ([WidgetType]).
 * @property colSpan     Numero di colonne occupate nella griglia a 6 (2, 3, 4 o 6).
 * @property heightStep  Livello di dettaglio verticale ([WidgetHeightStep]).
 * @property position    Ordine di visualizzazione (0 = primo in alto).
 * @property periodo     Intervallo temporale per i calcoli ([WidgetPeriodo]).
 * @property topN        Numero massimo di elementi per [WidgetType.TOP_CATEGORIE]
 *                       e [WidgetType.ULTIMI_MOVIMENTI].
 * @property contoFilter Filtro per conto specifico (usato da [WidgetType.SALDO_CONTO]).
 */
data class WidgetConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: WidgetType,
    val colSpan: Int = type.defaultColSpan(),
    val heightStep: WidgetHeightStep = type.defaultHeightStep(),
    val position: Int = 0,
    val periodo: WidgetPeriodo = WidgetPeriodo.MESE_CORRENTE,
    val topN: Int = 5,
    val contoFilter: String? = null
)

/**
 * Layout di default applicato quando l'utente non ha ancora personalizzato
 * la propria dashboard.
 */
fun defaultDashboardLayout(): List<WidgetConfig> = listOf(
    WidgetConfig(type = WidgetType.SALDO_MESE,         colSpan = 6, position = 0),
    WidgetConfig(type = WidgetType.TOTALE_USCITE,      colSpan = 3, position = 1),
    WidgetConfig(type = WidgetType.TOTALE_ENTRATE,     colSpan = 3, position = 2),
    WidgetConfig(type = WidgetType.GRAFICO_TORTA,      colSpan = 6, position = 3),
    WidgetConfig(type = WidgetType.TOP_CATEGORIE,      colSpan = 6, position = 4),
    WidgetConfig(type = WidgetType.ULTIMI_MOVIMENTI,   colSpan = 6, position = 5)
)
