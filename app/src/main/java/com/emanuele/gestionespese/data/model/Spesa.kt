package com.emanuele.gestionespese.data.model

import com.google.gson.annotations.SerializedName

// --- Envelope standard Apps Script: { ok: true, data: ... } oppure { error: "..." } ---
data class ApiEnvelope<T>(
    val ok: Boolean? = null,
    val data: T? = null,
    val error: String? = null
)

// --- Lettura lista spese (da vista già risolta lato server) ---
data class SpesaView(
    val id: Int,
    val utente: String? = null,
    val data: String? = null,
    val conto: String? = null,
    val importo: Double = 0.0,
    val tipo: String? = null,
    val tipo_movimento: String? = null,
    val categoria: String? = null,
    val sottocategoria: String? = null,
    val descrizione: String? = null,
    val mese: Int? = null,
    val anno: Int? = null
)

// --- Campi scrivibili su SPESE (usato in insert/update) ---
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

// --- Payload CRUD verso Apps Script ---
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

// --- Lookup con struttura nota ---
data class CategoriaRow(
    val id: Int,
    val descrizione: String? = null,
    @SerializedName(value = "attiva", alternate = ["attivo"])
    val attiva: Any? = null
)

// --- Utente autenticato ---
data class UtenteRow(
    val id: Int,
    @SerializedName(value = "utenza", alternate = ["utenza"])
    val utente: String? = null,
    val nome: String? = null,
    val cognome: String? = null,
    @SerializedName(value = "attivo", alternate = ["attiva"])
    val attivo: Any? = null,
    val email: String? = null
)

// --- Coppia categoria/sottocategoria (usata nel repository e nel DAO) ---
data class SottoCatItem(
    val categoria: String,
    val sottocategoria: String
)

// --- UTC: abbinamento utenza-tipologia-categoria-sottocategoria ---
data class UtcItem(
    val utente: String,
    val tipologia: String,
    val categoria: String,
    val sottocategoria: String,
    val attivo: Boolean,
    val tipoMovimento: String? = null
)

data class LoginRequest(
    val resource: String = "utente",
    val op: String = "login",
    val utenza: String,
    val password: String
)

data class LinkGoogleRequest(
    val resource: String = "utente",
    val op: String = "link_google",
    val id: Int,
    val google_id: String
)

data class UnlinkGoogleRequest(
    val resource: String = "utente",
    val op: String = "unlink_google",
    val id: Int
)

data class GoogleLoginRequest(
    val resource: String = "utente",
    val op: String = "login_google",
    val google_id: String
)

data class SaveDashboardRequest(
    val resource: String = "dashboard",
    val op: String = "upsert",
    val utente: String,
    val layout_json: String
)

data class TipologiaRow(
    val id: Int,
    val descrizione: String? = null,
    val attivo: Any? = null,
    val tipo_movimento: String? = null
)

data class GenericInsertRequest(
    val resource: String,
    val op: String = "insert",
    val data: Map<String, Any?>
)

data class GenericUpdateRequest(
    val resource: String,
    val op: String = "update",
    val id: Int,
    val data: Map<String, Any?>
)

data class GenericDeleteRequest(
    val resource: String,
    val op: String = "delete",
    val id: Int
)