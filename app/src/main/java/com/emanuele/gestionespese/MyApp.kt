/**
 * MyApp.kt
 *
 * Singleton [Application] dell'app. Viene creato prima di qualsiasi Activity e
 * mantiene le dipendenze globali (database Room, client Retrofit) e la sessione
 * utente corrente in memoria e in SharedPreferences.
 */
package com.emanuele.gestionespese

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.emanuele.gestionespese.data.local.AppDatabase
import com.emanuele.gestionespese.data.local.MIGRATION_12_13
import com.emanuele.gestionespese.data.local.MIGRATION_13_14
import com.emanuele.gestionespese.data.local.MIGRATION_14_15
import com.emanuele.gestionespese.data.local.MIGRATION_15_16
import com.emanuele.gestionespese.data.local.WebankSeed
import com.emanuele.gestionespese.data.remote.RetrofitProvider
import com.emanuele.gestionespese.data.remote.SupabaseApi
import com.emanuele.gestionespese.utils.DevLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Entry point dell'applicazione. Inizializza Room e Retrofit all'avvio e
 * gestisce la sessione utente tramite SharedPreferences.
 */
class MyApp : Application() {

    /** Istanza del database Room, disponibile dopo [onCreate]. */
    lateinit var db: AppDatabase
        private set

    /** Client API Retrofit, disponibile dopo [onCreate]. */
    lateinit var api: SupabaseApi
        private set

    private lateinit var prefs: SharedPreferences

    /** Nome visualizzato dell'utente loggato, o `null` se non autenticato. */
    var currentUserLabel: String? = null

    /** ID numerico dell'utente loggato, o `null` se non autenticato. */
    var currentUserId: Int? = null

    /** `true` se l'utente ha collegato un account Google. */
    var currentGoogleLinked: Boolean = false

    /** `true` se l'autenticazione biometrica è abilitata. */
    var biometricEnabled: Boolean = false

    /** `true` se all'utente è già stato chiesto di attivare la biometria. */
    var biometricAsked: Boolean = false

    /** `true` se esiste una sessione attiva (utente loggato). */
    var sessionActive: Boolean = false

    /** `true` se la modalità sviluppatore è abilitata (persiste tra i riavvii). */
    var devModeEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("gs_session", Context.MODE_PRIVATE)

        currentUserLabel    = prefs.getString("user_label", null)
        currentUserId       = prefs.getInt("user_id", -1).takeIf { it != -1 }
        currentGoogleLinked = prefs.getBoolean("google_linked", false)
        biometricEnabled    = prefs.getBoolean("biometric_enabled", false)
        biometricAsked      = prefs.getBoolean("biometric_asked", false)
        sessionActive       = prefs.getBoolean("session_active", false)
        devModeEnabled      = prefs.getBoolean("dev_mode_enabled", false)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "gestione_spese.db"
        )
            .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

        // Seed del profilo Webank (idempotente — non fa nulla se già presente)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WebankSeed.seedIfNeeded(db.bankProfileDao())
            } catch (t: Throwable) {
                DevLogger.log("SEED", "Errore seed Webank: ${t.message}")
            }
        }

        val retrofit = RetrofitProvider.create(
            baseUrl = getString(R.string.backend_url),
            apiKey  = getString(R.string.backend_api_key)
        )
        api = retrofit.create(SupabaseApi::class.java)
    }

    /**
     * Persiste i dati di sessione dopo il login.
     *
     * @param userLabel    Nome visualizzato dell'utente.
     * @param userId       ID numerico dell'utente.
     * @param googleLinked `true` se l'account Google è collegato.
     */
    fun saveSession(userLabel: String, userId: Int, googleLinked: Boolean) {
        currentUserLabel    = userLabel
        currentUserId       = userId
        currentGoogleLinked = googleLinked
        sessionActive       = true
        prefs.edit()
            .putString("user_label", userLabel)
            .putInt("user_id", userId)
            .putBoolean("google_linked", googleLinked)
            .putBoolean("session_active", true)
            .apply()
    }

    /**
     * Salva la preferenza biometrica dell'utente.
     *
     * @param enabled `true` per abilitare il login biometrico.
     */
    fun saveBiometricEnabled(enabled: Boolean) {
        biometricEnabled = enabled
        biometricAsked   = true
        prefs.edit()
            .putBoolean("biometric_enabled", enabled)
            .putBoolean("biometric_asked", true)
            .apply()
    }

    /**
     * Salva la preferenza della modalità sviluppatore.
     *
     * @param enabled `true` per abilitare la modalità sviluppatore.
     */
    fun saveDevModeEnabled(enabled: Boolean) {
        devModeEnabled = enabled
        prefs.edit()
            .putBoolean("dev_mode_enabled", enabled)
            .apply()
    }

    /**
     * Imposta la sessione solo in memoria, senza persistere su SharedPreferences.
     * Usato quando l'utente non spunta "Ricordami": la sessione viene persa al riavvio.
     *
     * @param userLabel    Nome visualizzato dell'utente.
     * @param userId       ID numerico dell'utente.
     * @param googleLinked `true` se l'account Google è collegato.
     */
    fun setTemporarySession(userLabel: String, userId: Int, googleLinked: Boolean) {
        currentUserLabel    = userLabel
        currentUserId       = userId
        currentGoogleLinked = googleLinked
        sessionActive       = true
    }

    /**
     * Cancella la sessione corrente (logout).
     * Rimuove tutti i dati utente dalla memoria e dalle SharedPreferences.
     */
    fun clearSession() {
        currentUserLabel    = null
        currentUserId       = null
        currentGoogleLinked = false
        sessionActive       = false
        prefs.edit()
            .remove("user_label")
            .remove("user_id")
            .remove("google_linked")
            .putBoolean("session_active", false)
            .apply()
    }
}
