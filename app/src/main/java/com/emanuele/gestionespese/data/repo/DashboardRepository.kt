/**
 * DashboardRepository.kt
 *
 * Repository per il layout personalizzato della dashboard. Gestisce:
 * - La persistenza locale del layout in Room ([DashboardDao])
 * - La sincronizzazione bidirezionale con il backend remoto ([SupabaseApi])
 *
 * Il layout è serializzato/deserializzato come JSON di `List<WidgetConfig>` tramite Gson.
 * In caso di errori di sync o parsing, viene sempre restituito il [defaultDashboardLayout].
 */
package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.DashboardDao
import com.emanuele.gestionespese.data.local.entities.DashboardEntity
import com.emanuele.gestionespese.data.model.SaveDashboardRequest
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.defaultDashboardLayout
import com.emanuele.gestionespese.data.remote.SupabaseApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Gestisce la persistenza e la sincronizzazione del layout della dashboard.
 *
 * @param dao DAO Room per la tabella `dashboard`.
 * @param api Client API per la sincronizzazione remota.
 */
class DashboardRepository(
    private val dao: DashboardDao,
    private val api: SupabaseApi
) {
    private val gson = Gson()

    // ── ROOM ─────────────────────────────────────────────────────────────

    /**
     * Carica il layout della dashboard dal DB locale.
     * Se non è ancora stato salvato o il JSON non è valido, restituisce [defaultDashboardLayout].
     *
     * @param utente ID dell'utente.
     * @return Lista di [WidgetConfig] ordinata per posizione.
     */
    suspend fun getLayout(utente: String): List<WidgetConfig> {
        val entity = dao.getByUtente(utente)
        if (entity == null) return defaultDashboardLayout()
        return try {
            val type = object : TypeToken<List<WidgetConfig>>() {}.type
            gson.fromJson(entity.layoutJson, type) ?: defaultDashboardLayout()
        } catch (e: Exception) {
            defaultDashboardLayout()
        }
    }

    /**
     * Salva il layout nel DB locale (e poi sincronizza in background).
     *
     * @param utente  ID dell'utente.
     * @param widgets Lista di widget da salvare.
     */
    suspend fun saveLayout(utente: String, widgets: List<WidgetConfig>) {
        val json = gson.toJson(widgets)
        dao.upsert(DashboardEntity(utente = utente, layoutJson = json))
    }

    // ── SYNC remoto → Room ───────────────────────────────────────────────

    /**
     * Scarica il layout dal backend e lo sovrascrive nel DB locale.
     * Errori di rete vengono ignorati silenziosamente (fallback su dato locale).
     *
     * @param utente ID dell'utente.
     */
    suspend fun syncFromRemote(utente: String) {
        try {
            val response = api.getDashboard(utente = utente)
            val row = response.data?.firstOrNull() ?: return
            val json = (row["layout_json"] as? String) ?: return
            dao.upsert(DashboardEntity(utente = utente, layoutJson = json))
        } catch (e: Exception) {
            // Se il sync fallisce usiamo quello locale — non blocchiamo
        }
    }

    /**
     * Carica il layout sul backend. Errori di rete ignorati (dato già in Room).
     *
     * @param utente  ID dell'utente.
     * @param widgets Layout da sincronizzare.
     */
    suspend fun syncToRemote(utente: String, widgets: List<WidgetConfig>) {
        try {
            val json = gson.toJson(widgets)
            api.saveDashboard(
                SaveDashboardRequest(
                    resource = "dashboard",
                    op = "upsert",
                    utente = utente,
                    layout_json = json
                )
            )
        } catch (e: Exception) {
            // Sync fallisce silenziosamente — i dati sono già in Room
        }
    }

    // ── SYNC completo (usato da syncAll) ─────────────────────────────────

    /**
     * Esegue la sincronizzazione completa (attualmente solo da remoto verso locale).
     * Chiamato da [SpeseViewModel.syncAll].
     *
     * @param utente ID dell'utente.
     */
    suspend fun syncAll(utente: String) {
        syncFromRemote(utente)
    }
}