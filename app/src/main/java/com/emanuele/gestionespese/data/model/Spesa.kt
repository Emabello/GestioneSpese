/**
 * Spesa.kt
 *
 * Modelli di dati per la comunicazione con il backend Google Apps Script.
 * Contiene:
 * - [ApiEnvelope]: wrapper standard per tutte le risposte del backend
 * - [SpesaView]: DTO per la lettura dei movimenti (risposta GET)
 * - [SpesaPatch]: DTO per i campi scrivibili (corpo di INSERT/UPDATE)
 * - Request types: [InsertRequest], [UpdateRequest], [DeleteRequest] per le operazioni CRUD
 * - Lookup types: [CategoriaRow], [SottoCatItem], [UtcItem] per le tabelle di riferimento
 * - Auth types: [LoginRequest], [GoogleLoginRequest], [LinkGoogleRequest], [UnlinkGoogleRequest]
 * - [SaveDashboardRequest]: payload per il salvataggio del layout dashboard
 */
package com.emanuele.gestionespese.data.model

import com.google.gson.annotations.SerializedName

/**
 * Wrapper standard per tutte le risposte del backend Apps Script.
 * Il backend risponde sempre con `{ ok: true, data: ... }` o `{ error: "..." }`.
 *
 * @param T Tipo del payload `data` (es. `List<SpesaView>`, `Map<String, Any?>`).
 */
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

/**
 * Payload dell'endpoint batch GAS `resource=sync_all`.
 * Contiene tutti i dati necessari alla sincronizzazione in un'unica risposta.
 * Tutti i campi sono nullable per resilienza a risposte parziali o fallback parziale.
 */
data class SyncAllData(
    val tipologie:      List<Map<String, Any?>>? = null,
    val categorie:      List<Map<String, Any?>>? = null,
    val sottocategorie: List<Map<String, Any?>>? = null,
    val conti:          List<Map<String, Any?>>? = null,
    val utcs:           List<Map<String, Any?>>? = null,
    val spese:          List<SpesaView>? = null
)