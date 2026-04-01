/**
 * DashboardRepository.kt
 *
 * Repository per il layout personalizzato della dashboard.
 * Gestisce la persistenza locale (Room) e la sync remota (API).
 *
 * Backward-compatibility: i JSON salvati con il vecchio sistema SMALL/WIDE
 * vengono automaticamente migrati al nuovo sistema colSpan/heightStep.
 */
package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.DashboardDao
import com.emanuele.gestionespese.data.local.entities.DashboardEntity
import com.emanuele.gestionespese.data.model.SaveDashboardRequest
import com.emanuele.gestionespese.data.model.WidgetConfig
import com.emanuele.gestionespese.data.model.WidgetHeightStep
import com.emanuele.gestionespese.data.model.WidgetType
import com.emanuele.gestionespese.data.model.defaultColSpan
import com.emanuele.gestionespese.data.model.defaultDashboardLayout
import com.emanuele.gestionespese.data.model.defaultHeightStep
import com.emanuele.gestionespese.data.remote.SupabaseApi
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

class DashboardRepository(
    private val dao: DashboardDao,
    private val api: SupabaseApi
) {
    private val gson = Gson()

    // ── ROOM ─────────────────────────────────────────────────────────────────

    suspend fun getLayout(utente: String): List<WidgetConfig> {
        val entity = dao.getByUtente(utente) ?: return defaultDashboardLayout()
        return parseLayoutJson(entity.layoutJson)
    }

    suspend fun saveLayout(utente: String, widgets: List<WidgetConfig>) {
        val json = gson.toJson(widgets)
        dao.upsert(DashboardEntity(utente = utente, layoutJson = json))
    }

    // ── SYNC remoto → Room ───────────────────────────────────────────────────

    suspend fun syncFromRemote(utente: String) {
        try {
            val response = api.getDashboard(utente = utente)
            val row  = response.data?.firstOrNull() ?: return
            val json = (row["layout_json"] as? String) ?: return
            // Migra anche il JSON remoto prima di salvarlo
            val migrated = gson.toJson(parseLayoutJson(json))
            dao.upsert(DashboardEntity(utente = utente, layoutJson = migrated))
        } catch (_: Exception) { }
    }

    suspend fun syncToRemote(utente: String, widgets: List<WidgetConfig>) {
        try {
            val json = gson.toJson(widgets)
            api.saveDashboard(
                SaveDashboardRequest(
                    resource    = "dashboard",
                    op          = "upsert",
                    utente      = utente,
                    layout_json = json
                )
            )
        } catch (_: Exception) { }
    }

    suspend fun syncAll(utente: String) {
        syncFromRemote(utente)
    }

    // ── Parsing con migrazione backward-compat ───────────────────────────────

    /**
     * Deserializza il JSON del layout gestendo sia il formato nuovo (colSpan/heightStep)
     * sia il vecchio formato (size: "SMALL"/"WIDE"). Applica valori di default mancanti.
     */
    private fun parseLayoutJson(json: String): List<WidgetConfig> {
        return try {
            val arr = gson.fromJson(json, JsonArray::class.java) ?: return defaultDashboardLayout()
            arr.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    migrateWidgetJson(obj)
                } catch (_: Exception) { null }
            }.ifEmpty { defaultDashboardLayout() }
        } catch (_: Exception) {
            defaultDashboardLayout()
        }
    }

    /**
     * Converte un singolo JsonObject dal formato vecchio al nuovo.
     * - Se ha "size"="SMALL" → colSpan=3, heightStep=defaultHeightStep
     * - Se ha "size"="WIDE"  → colSpan=6, heightStep=defaultHeightStep
     * - Se ha già "colSpan"  → usa quello, aggiunge heightStep se mancante
     */
    private fun migrateWidgetJson(obj: JsonObject): WidgetConfig? {
        // Legge il tipo
        val typeName = obj.get("type")?.asString ?: return null
        val type = try { WidgetType.valueOf(typeName) } catch (_: Exception) { return null }

        // Determina colSpan
        val colSpan: Int = when {
            obj.has("colSpan") -> obj.get("colSpan").asInt
            obj.has("size")    -> when (obj.get("size").asString) {
                "SMALL" -> 3
                "WIDE"  -> 6
                else    -> type.defaultColSpan()
            }
            else -> type.defaultColSpan()
        }

        // Determina heightStep
        val heightStep: WidgetHeightStep = when {
            obj.has("heightStep") -> try {
                WidgetHeightStep.valueOf(obj.get("heightStep").asString)
            } catch (_: Exception) { type.defaultHeightStep() }
            else -> type.defaultHeightStep()
        }

        return WidgetConfig(
            id          = obj.get("id")?.asString       ?: java.util.UUID.randomUUID().toString(),
            type        = type,
            colSpan     = colSpan.coerceIn(2, 6),
            heightStep  = heightStep,
            position    = obj.get("position")?.asInt    ?: 0,
            periodo     = try {
                com.emanuele.gestionespese.data.model.WidgetPeriodo
                    .valueOf(obj.get("periodo")?.asString ?: "MESE_CORRENTE")
            } catch (_: Exception) {
                com.emanuele.gestionespese.data.model.WidgetPeriodo.MESE_CORRENTE
            },
            topN        = obj.get("topN")?.asInt        ?: 5,
            contoFilter = obj.get("contoFilter")?.let {
                if (it.isJsonNull) null else it.asString
            }
        )
    }
}
