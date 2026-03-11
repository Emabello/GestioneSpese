package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.model.SottoCatItem

data class LookupsLocal(
    val tipi: List<String>,
    val categorie: List<String>,
    val sottocategorie: List<SottoCatItem>,
    val conti: List<String>
)