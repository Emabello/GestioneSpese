package com.emanuele.gestionespese

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.emanuele.gestionespese.data.local.AppDatabase
import com.emanuele.gestionespese.data.remote.RetrofitProvider
import com.emanuele.gestionespese.data.remote.SupabaseApi

class MyApp : Application() {

    lateinit var db: AppDatabase
        private set

    lateinit var api: SupabaseApi
        private set

    private lateinit var prefs: SharedPreferences

    var currentUserLabel: String? = null
    var currentUserId: Int? = null
    var currentGoogleLinked: Boolean = false
    var biometricEnabled: Boolean = false
    var biometricAsked: Boolean = false
    var sessionActive: Boolean = false

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("gs_session", Context.MODE_PRIVATE)

        currentUserLabel    = prefs.getString("user_label", null)
        currentUserId       = prefs.getInt("user_id", -1).takeIf { it != -1 }
        currentGoogleLinked = prefs.getBoolean("google_linked", false)
        biometricEnabled    = prefs.getBoolean("biometric_enabled", false)
        biometricAsked      = prefs.getBoolean("biometric_asked", false)
        sessionActive    = prefs.getBoolean("session_active", false)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "gestione_spese.db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        val retrofit = RetrofitProvider.create(
            baseUrl = getString(R.string.backend_url),
            apiKey  = getString(R.string.backend_api_key)
        )
        api = retrofit.create(SupabaseApi::class.java)
    }

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

    fun saveBiometricEnabled(enabled: Boolean) {
        biometricEnabled = enabled
        biometricAsked   = true
        prefs.edit()
            .putBoolean("biometric_enabled", enabled)
            .putBoolean("biometric_asked", true)
            .apply()
    }

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