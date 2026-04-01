/**
 * WidgetRenderer.kt
 *
 * Composable dispatcher che instrada ogni [WidgetConfig] al widget specifico.
 * Riceve le spese già filtrate per il mese selezionato dalla SummaryScreen.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.WidgetType

/**
 * @param config          Configurazione del widget.
 * @param spese           Spese filtrate per il mese selezionato.
 * @param spesePrevMonth  Spese del mese precedente (per confronto %).
 * @param speseAll        Tutte le spese non filtrate (per saldo conto totale e andamento).
 */
@Composable
fun WidgetRenderer(
    config: WidgetConfig,
    spese: List<SpesaView>,
    spesePrevMonth: List<SpesaView> = emptyList(),
    speseAll: List<SpesaView> = emptyList(),
    modifier: Modifier = Modifier
) {
    when (config.type) {
        WidgetType.TOTALE_USCITE    -> TotaleUsciteWidget(config, spese, spesePrevMonth, modifier)
        WidgetType.TOTALE_ENTRATE   -> TotaleEntrateWidget(config, spese, spesePrevMonth, modifier)
        WidgetType.SALDO_MESE       -> SaldoMeseWidget(config, spese, spesePrevMonth, modifier)
        WidgetType.GRAFICO_TORTA    -> GraficoTortaWidget(config, spese, modifier)
        WidgetType.ULTIMI_MOVIMENTI -> UltimiMovimentiWidget(config, spese, modifier)
        WidgetType.TOP_CATEGORIE    -> TopCategorieWidget(config, spese, modifier)
        WidgetType.SALDO_CONTO      -> SaldoContoWidget(config, spese, speseAll, modifier)
        WidgetType.ANDAMENTO_MENSILE -> AndamentoMensileWidget(config, speseAll, modifier)
    }
}
