package com.emanuele.gestionespese.data.model

import com.google.gson.annotations.SerializedName

data class UtcItem(
    val utente: String,        // "1 - E.BELLOTTI"
    val tipologia: String,     // "1 - Debiti"
    val categoria: String,     // "5 - Finanziamento"
    val sottocategoria: String, // "4 - Finanziamento Macchina"
    val attivo: Boolean
)
// Wrapper standard: Apps Script risponde { data: ... } oppure { ok:true, data: ... }
data class ApiEnvelope<T>(
    val ok: Boolean? = null,
    val data: T? = null,
    val error: String? = null
)

/**
 * Riga "SPESE"
 * headers: id, utente, data, conto, importo, tipo, categoria, sottocategoria, descrizione, mese, anno, lista_conto_riga
 */


// Lettura lista: tipicamente da v_spese (nomi già risolti)
data class SpesaView(
    val id: Int,
    val utente: String? = null,
    val data: String? = null,
    @SerializedName("conto")
    val conto: String? = null,
    val importo: Double = 0.0,
    val tipo: String? = null,
    val categoria: String? = null,
    val sottocategoria: String? = null,
    val descrizione: String? = null,
    val mese: Int? = null,
    val anno: Int? = null
)

/** Riga "CATEGORIA" */
data class CategoriaRow(
    val id: Int,
    val descrizione: String? = null,
    // nello script è "attiva" (header), ma alt "attivo"
    @SerializedName(value = "attiva", alternate = ["attivo"])
    val attiva: Any? = null
)

/** Riga "SOTTOCATEGORIA" */
data class SottocategoriaRow(
    val id: Int,
    val descrizione: String? = null,
    // nello script è "attiva" (header), ma alt "attivo"
    @SerializedName(value = "attiva", alternate = ["attivo"])
    val attiva: Any? = null
)

/** Riga "UTENTE" */
data class UtenteRow(
    val id: Int,
    @SerializedName(value = "utenza", alternate = ["utente"])
    val utente: String? = null,
    val nome: String? = null,
    val cognome: String? = null,
    @SerializedName(value = "attivo", alternate = ["attiva"])
    val attivo: Any? = null,
    val email: String? = null
)

/** Riga "TIPOLOGIA" (quello che in app chiamavi "Tipo") */
data class TipologiaRow(
    val id: Int,
    val descrizione: String? = null,
    @SerializedName(value = "attivo", alternate = ["attiva"])
    val attivo: Any? = null
)

/** Riga "CONTO" */
data class ContoRow(
    val id: Int,
    val descrizione: String? = null,
    @SerializedName(value = "attivo", alternate = ["attiva"])
    val attivo: Any? = null
)

/** Riga "SOTTOCATEGORIA" */
data class SottoCategoriaRow(
    val id: Int,
    @SerializedName("id_categoria")
    val idCategoria: Int,
    val descrizione: String? = null,
    @SerializedName(value = "attivo", alternate = ["attiva"])
    val attivo: Any? = null
)

/** Riga "UC" (utente-conto) */
data class UcRow(
    val id: Int,
    @SerializedName("id_utente")
    val idUtente: Int,
    @SerializedName("id_conto")
    val idConto: Int,
    @SerializedName(value = "attivo", alternate = ["attiva"])
    val attivo: Any? = null
)

/** Payload per insert/update/delete secondo il tuo Apps Script */
data class InsertRequest<T>(
    val resource: String,
    val utente: String,
    val data: T
)

data class UpdateRequest<T>(
    val resource: String,
    val op: String = "update",
    val id: Int,
    val data: T
)

data class DeleteRequest(
    val resource: String,
    val op: String = "delete",
    val id: Int
)

/** Campi scrivibili su SPESE */
data class SpesaPatch(
    val utente: String? = null,
    val data: String? = null,
    val conto: String? = null,
    val importo: Double? = null,
    val tipo: String? = null,
    val categoria: String? = null,
    val sottocategoria: String? = null,
    val descrizione: String? = null
)

data class SottoCatItem(
    val categoria: String,      // qui ci mettiamo l’ID categoria (es. "1")
    val sottocategoria: String  // descrizione sottocategoria
data class LoginRequest(
    @SerializedName("p_utente") val utente: String,                 // "YYYY-MM-DD"
    @SerializedName("p_password") val password: String?,   // nullable
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

data class LoginRow(
    val id: Int,
    val utente: String?,
    val password: String?,
    val nome: String?,
    val cognome: String?,
    val attivo: Boolean?,
    val email: String?
)