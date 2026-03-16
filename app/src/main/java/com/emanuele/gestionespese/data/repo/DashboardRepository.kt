package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.DashboardDao
import com.emanuele.gestionespese.data.local.entities.DashboardEntity
import com.emanuele.gestionespese.data.model.SaveDashboardRequest
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.defaultDashboardLayout
import com.emanuele.gestionespese.data.remote.SupabaseApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DashboardRepository(
    private val dao: DashboardDao,
    private val api: SupabaseApi
) {
    private val gson = Gson()

    // ── ROOM ─────────────────────────────────────────────────────────────

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

    suspend fun saveLayout(utente: String, widgets: List<WidgetConfig>) {
        val json = gson.toJson(widgets)
        dao.upsert(DashboardEntity(utente = utente, layoutJson = json))
    }

    // ── SYNC remoto → Room ───────────────────────────────────────────────

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

    suspend fun syncAll(utente: String) {
        syncFromRemote(utente)
    }
}