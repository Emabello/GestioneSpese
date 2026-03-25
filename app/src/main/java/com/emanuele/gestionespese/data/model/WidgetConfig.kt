/**
 * WidgetConfig.kt
 *
 * Modelli di configurazione per i widget della dashboard personalizzabile.
 * Definisce i tipi di widget disponibili, le dimensioni e i periodi temporali,
 * oltre al layout default applicato ai nuovi utenti.
 */
package com.emanuele.gestionespese.data.model

import java.util.UUID

/** Identifica il tipo di contenuto visualizzato dal widget. */
enum class WidgetType {
    /** Totale delle uscite nel periodo selezionato. */
    TOTALE_USCITE,
    /** Totale delle entrate nel periodo selezionato. */
    TOTALE_ENTRATE,
    /** Saldo (entrate – uscite) del periodo selezionato. */
    SALDO_MESE,
    /** Grafico a torta delle spese per categoria. */
    GRAFICO_TORTA,
    /** Lista degli ultimi movimenti registrati. */
    ULTIMI_MOVIMENTI,
    /** Classifica delle categorie per importo speso. */
    TOP_CATEGORIE,
    /** Saldo di uno specifico conto (tutti i tempi). */
    SALDO_CONTO,
    /** Grafico a barre dell'andamento mensile (ultimi 6 mesi). */
    ANDAMENTO_MENSILE
}

/** Dimensione del widget nella griglia della dashboard (2 colonne). */
enum class WidgetSize {
    /** Occupa mezza larghezza (1 colonna su 2). */
    SMALL,
    /** Occupa la larghezza intera (2 colonne). */
    WIDE
}

/** Intervallo temporale su cui il widget calcola i dati. */
enum class WidgetPeriodo {
    /** Dal primo all'ultimo giorno del mese corrente. */
    MESE_CORRENTE,
    /** Ultimi 30 giorni a partire da oggi. */
    ULTIMI_30_GIORNI,
    /** Dal primo gennaio al giorno corrente dell'anno. */
    ANNO_CORRENTE
}

/**
 * Configurazione di un singolo widget nella dashboard.
 *
 * @property id          Identificativo UUID univoco del widget.
 * @property type        Tipo di contenuto ([WidgetType]).
 * @property size        Dimensione nella griglia ([WidgetSize]).
 * @property position    Ordine di visualizzazione (0 = primo in alto).
 * @property periodo     Intervallo temporale per i calcoli ([WidgetPeriodo]).
 * @property topN        Numero massimo di elementi per [WidgetType.TOP_CATEGORIE]
 *                       e [WidgetType.ULTIMI_MOVIMENTI].
 * @property contoFilter Filtro per conto specifico (usato da [WidgetType.SALDO_CONTO]).
 *                       `null` = mostra tutti i conti / usa il primo disponibile.
 */
data class WidgetConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: WidgetType,
    val size: WidgetSize = WidgetSize.WIDE,
    val position: Int = 0,
    val periodo: WidgetPeriodo = WidgetPeriodo.MESE_CORRENTE,
    val topN: Int = 5,
    val contoFilter: String? = null
)

/**
 * Layout di default applicato quando l'utente non ha ancora personalizzato
 * la propria dashboard.
 *
 * @return Lista ordinata di [WidgetConfig] con i widget principali.
 */
fun defaultDashboardLayout(): List<WidgetConfig> = listOf(
    WidgetConfig(type = WidgetType.SALDO_MESE,         size = WidgetSize.WIDE,  position = 0),
    WidgetConfig(type = WidgetType.TOTALE_USCITE,      size = WidgetSize.SMALL, position = 1),
    WidgetConfig(type = WidgetType.TOTALE_ENTRATE,     size = WidgetSize.SMALL, position = 2),
    WidgetConfig(type = WidgetType.GRAFICO_TORTA,      size = WidgetSize.WIDE,  position = 3),
    WidgetConfig(type = WidgetType.TOP_CATEGORIE,      size = WidgetSize.WIDE,  position = 4),
    WidgetConfig(type = WidgetType.ULTIMI_MOVIMENTI,   size = WidgetSize.WIDE,  position = 5)
)
