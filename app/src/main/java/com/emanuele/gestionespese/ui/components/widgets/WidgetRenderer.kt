/**
 * WidgetRenderer.kt
 *
 * Composable dispatcher che instrada ogni [WidgetConfig] al widget specifico
 * corrispondente al suo [WidgetType]. Centralizza la logica di selezione
 * del composable da renderizzare in base al tipo.
 */
package com.emanuele.gestionespese.ui.components.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.emanuele.gestionespese.data.model.SpesaView
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.WidgetType

@Composable
fun WidgetRenderer(
    config: WidgetConfig,
    spese: List<SpesaView>,
    modifier: Modifier = Modifier
) {
    when (config.type) {
        WidgetType.TOTALE_USCITE       -> TotaleUsciteWidget(config, spese, modifier)
        WidgetType.TOTALE_ENTRATE      -> TotaleEntrateWidget(config, spese, modifier)
        WidgetType.SALDO_MESE          -> SaldoMeseWidget(config, spese, modifier)
        WidgetType.GRAFICO_TORTA       -> GraficoTortaWidget(config, spese, modifier)
        WidgetType.ULTIMI_MOVIMENTI    -> UltimiMovimentiWidget(config, spese, modifier)
        WidgetType.TOP_CATEGORIE       -> TopCategorieWidget(config, spese, modifier)
        WidgetType.SALDO_CONTO         -> SaldoContoWidget(config, spese, modifier)
        WidgetType.ANDAMENTO_MENSILE   -> AndamentoMensileWidget(config, spese, modifier)
        WidgetType.CONFRONTO_MESE      -> ConfrontoMeseWidget(config, spese, modifier)
        WidgetType.RISPARMIO_CUMULATIVO -> RisparmioCumulativoWidget(config, spese, modifier)
    }
}