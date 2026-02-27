package com.emanuele.gestionespese.data.model

import com.google.gson.annotations.SerializedName

// Lettura lista: tipicamente da v_spese (nomi già risolti)
data class SpesaView(
    val id: Int,
    val data: String? = null,
    val descrizione: String? = null,
    val importo: Double = 0.0,
    val tipo: String? = null,
    val mese: Int? = null,
    val anno: Int? = null,
    @SerializedName("metodo_pagamento")
    val metodoPagamento: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,

    // nuovi campi dalla VIEW
    @SerializedName("categoria_link_id")
    val categoriaLinkId: String? = null,
    @SerializedName("categoria_id")
    val categoriaId: String? = null,
    @SerializedName("sottocategoria_id")
    val sottocategoriaId: String? = null,

    val categoria: String? = null,
    val sottocategoria: String? = null
)

// Scrittura: su tabella spese (ID, non nomi)
data class SpesaUpsert(
    val id: Int? = null,
    val data: String,
    val importo: Double,
    val tipo: String,

    val mese: Int,
    val anno: Int,

    @SerializedName("categoria_link_id")
    val categoriaLinkId: String,

    @SerializedName("metodo_pagamento")
    val metodoPagamento: String,

    @SerializedName("descrizione")
    val note: String? = null
)

data class Categoria(
    val id: String,
    val nome: String,
    val ordine: Int? = null,
    val attiva: Boolean? = null
)

data class Sottocategoria(
    val id: String,
    val nome: String,
    val ordine: Int? = null,
    val attiva: Boolean? = null
)

data class LinkSottoRow(
    val ordine: Int? = null,
    @SerializedName("sottocategoria")
    val sottocategoria: Sottocategoria? = null
)

data class LinkIdRow(
    val id: String
)

data class RpcInsertSpesaRequest(
    @SerializedName("p_data") val data: String,                 // "YYYY-MM-DD"
    @SerializedName("p_descrizione") val descrizione: String?,   // nullable
    @SerializedName("p_importo") val importo: Double,
    @SerializedName("p_tipo") val tipo: String,
    @SerializedName("p_mese") val mese: Int,
    @SerializedName("p_anno") val anno: Int,
    @SerializedName("p_metodo_pagamento") val metodoPagamento: String,
    @SerializedName("p_categoria_link_id") val categoriaLinkId: String // UUID string
)

// Se vuoi leggere la riga che torna dalla function (opzionale)
data class SpesaRow(
    val id: Int,
    val data: String?,
    val descrizione: String?,
    val importo: Double?,
    val tipo: String?,
    val mese: Int?,
    val anno: Int?,
    @SerializedName("metodo_pagamento") val metodoPagamento: String?,
    @SerializedName("categoria_link_id") val categoriaLinkId: String?
)