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
    @SerializedName(value = "metodo_pagamento", alternate = ["conto", "conto_id"])
    val metodoPagamento: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName(value = "categoria_link_id", alternate = ["categoria_link"])
    val categoriaLinkId: String? = null,
    @SerializedName(value = "categoria_id", alternate = ["categoria"])
    val categoriaId: String? = null,
    @SerializedName(value = "sottocategoria_id", alternate = ["sottocategoria"])
    val sottocategoriaId: String? = null,

    val categoria: String? = null,
    val sottocategoria: String? = null
)

// Scrittura app (modello interno)
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

// Scrittura verso Apps Script (payload HTTP)
data class SpesaUpsertRequest(
    val resource: String = "spesa",
    val id: Int? = null,
    @SerializedName("utente_id") val utenteId: String = "2 - A.BERTOLI",
    @SerializedName("conto_id") val contoId: String,
    val data: String,
    val importo: Double,
    val tipo: String,
    val categoria: String,
    val sottocategoria: String,
    val descrizione: String?
)

data class UpdateSpesaRequest(
    val resource: String = "spesa_update",
    val id: Int,
    @SerializedName("conto_id") val contoId: String,
    val data: String,
    val importo: Double,
    val tipo: String,
    val categoria: String,
    val sottocategoria: String,
    val descrizione: String?
)

data class DeleteSpesaRequest(
    val resource: String = "spesa_delete",
    val id: Int
)

data class InsertSpesaResponse(
    val id: Int? = null,
    val ok: Boolean? = null
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
