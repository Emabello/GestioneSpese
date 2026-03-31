/**
 * WidgetConfig.kt
 *
 * Modelli di configurazione per i widget della dashboard personalizzabile.
 * Definisce i tipi di widget disponibili, la griglia a 6 colonne, gli step di altezza
 * e i periodi temporali, oltre al layout default applicato ai nuovi utenti.
 *
 * Sistema griglia:
 * - colSpan: 2 | 3 | 4 | 6  (colonne occupate su 6 totali)
 * - heightStep: S=110dp | M=190dp | L=300dp
 * - Resize con handle angolo in basso a destra (drag orizzontale = larghezza, verticale = altezza)
 */
package com.emanuele.gestionespese.data.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    ANDAMENTO_MENSILE,
    CONFRONTO_MESE,
    RISPARMIO_CUMULATIVO
}

/**
 * Step di altezza del widget. Ogni step corrisponde a un'altezza fissa in dp
 * e a un layout di contenuto diverso (compatto / standard / espanso).
 */
enum class WidgetHeightStep {
    /** Compatto: solo metrica principale. ~110dp */
    S,
    /** Standard: metrica + elemento visivo secondario. ~190dp */
    M,
    /** Espanso: tutto il contenuto, grafici completi. ~300dp */
    L;

    fun toDp(): Dp = when (this) {
        S -> 110.dp
        M -> 190.dp
        L -> 300.dp
    }
}

/** Intervallo temporale su cui il widget calcola i dati. */
enum class WidgetPeriodo {
    MESE_CORRENTE,
    ULTIMI_30_GIORNI,
    ANNO_CORRENTE
}

// ── Valori griglia validi ────────────────────────────────────────────────────
/** Valori di colSpan ammessi nella griglia a 6 colonne. */
val VALID_COL_SPANS = listOf(2, 3, 4, 6)

/** Colonne di default per tipo widget. */
fun WidgetType.defaultColSpan(): Int = when (this) {
    WidgetType.TOTALE_USCITE,
    WidgetType.TOTALE_ENTRATE,
    WidgetType.SALDO_CONTO     -> 3
    else                       -> 6
}

/** Numero minimo di colonne che un widget di questo tipo può occupare. */
fun WidgetType.minColSpan(): Int = when (this) {
    WidgetType.TOTALE_USCITE,
    WidgetType.TOTALE_ENTRATE,
    WidgetType.SALDO_MESE,
    WidgetType.SALDO_CONTO     -> 2
    WidgetType.ULTIMI_MOVIMENTI,
    WidgetType.TOP_CATEGORIE   -> 3
    else                       -> 4   // grafici
}

/** Altezza di default per tipo widget. */
fun WidgetType.defaultHeightStep(): WidgetHeightStep = when (this) {
    WidgetType.TOTALE_USCITE,
    WidgetType.TOTALE_ENTRATE,
    WidgetType.SALDO_MESE,
    WidgetType.SALDO_CONTO     -> WidgetHeightStep.S
    WidgetType.ULTIMI_MOVIMENTI,
    WidgetType.TOP_CATEGORIE   -> WidgetHeightStep.L
    else                       -> WidgetHeightStep.M
}

/**
 * Configurazione di un singolo widget nella dashboard.
 *
 * @property id          UUID univoco del widget.
 * @property type        Tipo di contenuto.
 * @property colSpan     Colonne occupate su 6 totali (2 | 3 | 4 | 6).
 * @property heightStep  Step di altezza (S / M / L).
 * @property position    Ordine di visualizzazione (0 = primo).
 * @property periodo     Intervallo temporale per i calcoli.
 * @property topN        Numero massimo di elementi per TOP_CATEGORIE e ULTIMI_MOVIMENTI.
 * @property contoFilter Conto specifico per SALDO_CONTO (null = primo disponibile).
 */
data class WidgetConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: WidgetType,
    val colSpan: Int = 6,
    val heightStep: WidgetHeightStep = WidgetHeightStep.M,
    val position: Int = 0,
    val periodo: WidgetPeriodo = WidgetPeriodo.MESE_CORRENTE,
    val topN: Int = 5,
    val contoFilter: String? = null
)

/** Layout di default per nuovi utenti. */
fun defaultDashboardLayout(): List<WidgetConfig> = listOf(
    WidgetConfig(type = WidgetType.SALDO_MESE,       colSpan = 6, heightStep = WidgetHeightStep.S, position = 0),
    WidgetConfig(type = WidgetType.TOTALE_USCITE,    colSpan = 3, heightStep = WidgetHeightStep.S, position = 1),
    WidgetConfig(type = WidgetType.TOTALE_ENTRATE,   colSpan = 3, heightStep = WidgetHeightStep.S, position = 2),
    WidgetConfig(type = WidgetType.GRAFICO_TORTA,    colSpan = 6, heightStep = WidgetHeightStep.M, position = 3),
    WidgetConfig(type = WidgetType.TOP_CATEGORIE,    colSpan = 6, heightStep = WidgetHeightStep.L, position = 4),
    WidgetConfig(type = WidgetType.ULTIMI_MOVIMENTI, colSpan = 6, heightStep = WidgetHeightStep.L, position = 5)
)
