package com.emanuele.gestionespese.data.local

import android.content.Context
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.data.local.entities.ParseRuleEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestisce il backup e il ripristino dei profili bancari su SharedPreferences.
 *
 * Ogni volta che un profilo viene creato, modificato o cancellato, il backup
 * viene aggiornato. Quando il database Room viene ricreato (es. downgrade
 * distruttivo), i profili vengono ripristinati automaticamente dal backup.
 */
class BankProfileBackupManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Salva tutti i profili e le relative regole di parsing su SharedPreferences.
     */
    suspend fun backupAll(dao: BankProfileDao) {
        val profiles = dao.getAllProfilesOnce()
        val arr = JSONArray()
        for (p in profiles) {
            val rules = dao.getRulesForProfile(p.id)
            val obj = JSONObject().apply {
                put("displayName", p.displayName)
                put("packageName", p.packageName)
                put("isActive", p.isActive)
                put("contentSource", p.contentSource)
                put("wizardSampleText", p.wizardSampleText ?: JSONObject.NULL)
                put("wizardSelections", p.wizardSelections ?: JSONObject.NULL)
                val rulesArr = JSONArray()
                for (r in rules) {
                    rulesArr.put(JSONObject().apply {
                        put("field", r.field)
                        put("regex", r.regex)
                        put("groupIndex", r.groupIndex)
                        put("priority", r.priority)
                        put("description", r.description)
                    })
                }
                put("rules", rulesArr)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    /**
     * Se la tabella bank_profile è vuota ma esiste un backup, ripristina
     * tutti i profili e le relative regole.
     */
    suspend fun restoreIfNeeded(dao: BankProfileDao) {
        val existing = dao.getAllProfilesOnce()
        if (existing.isNotEmpty()) return

        val json = prefs.getString(KEY_PROFILES, null) ?: return
        val arr = try { JSONArray(json) } catch (_: Exception) { return }

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val profileId = dao.insertProfile(
                BankProfileEntity(
                    displayName    = obj.optString("displayName", ""),
                    packageName    = obj.optString("packageName", ""),
                    isActive       = obj.optBoolean("isActive", true),
                    contentSource  = obj.optString("contentSource", "TEXT_OR_BIG"),
                    wizardSampleText = (obj.optString("wizardSampleText") ?: "")
                        .takeIf { it != "null" && it.isNotBlank() },
                    wizardSelections = (obj.optString("wizardSelections") ?: "")
                        .takeIf { it != "null" && it.isNotBlank() }
                )
            )
            if (profileId <= 0) continue

            val rulesArr = obj.optJSONArray("rules") ?: continue
            for (j in 0 until rulesArr.length()) {
                val rObj = rulesArr.optJSONObject(j) ?: continue
                dao.upsertRule(
                    ParseRuleEntity(
                        bankProfileId = profileId,
                        field         = rObj.optString("field", ""),
                        regex         = rObj.optString("regex", ""),
                        groupIndex    = rObj.optInt("groupIndex", 1),
                        priority      = rObj.optInt("priority", 0),
                        description   = rObj.optString("description", "")
                    )
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "bank_profile_backup"
        private const val KEY_PROFILES = "profiles_json"
    }
}
