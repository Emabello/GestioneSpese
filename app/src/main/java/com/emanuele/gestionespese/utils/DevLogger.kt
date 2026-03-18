/**
 * DevLogger.kt
 *
 * Logger in-memory per la modalità sviluppatore dell'app.
 * Mantiene un buffer circolare degli ultimi [MAX_LINES] eventi, visibili
 * nella schermata Impostazioni → sezione Sviluppatore.
 *
 * Usato da:
 *  - [com.emanuele.gestionespese.notifications.BankNotificationListener] per gli eventi di notifica bancaria
 *  - [com.emanuele.gestionespese.data.remote.RetrofitProvider] per le chiamate API
 *  - [com.emanuele.gestionespese.ui.screens.SettingsScreen] per la visualizzazione e pulizia dei log
 */
package com.emanuele.gestionespese.utils

import androidx.compose.runtime.mutableStateListOf

/** Singleton logger in-memory per il pannello sviluppatore. */
object DevLogger {
    private const val MAX_LINES = 200
    private val _logs = mutableStateListOf<String>()

    /** Lista immutabile dei log correnti, in ordine inverso (più recente prima). */
    val logs: List<String> get() = _logs

    /**
     * Aggiunge un'entry al log e la stampa anche in Logcat.
     *
     * @param tag  Categoria dell'evento (es. "API", "NOTIFICA").
     * @param msg  Testo del messaggio.
     */
    fun log(tag: String, msg: String) {
        val entry = "[$tag] $msg"
        android.util.Log.d(tag, msg)
        _logs.add(0, entry)
        if (_logs.size > MAX_LINES) _logs.removeLastOrNull()
    }

    /** Svuota il buffer dei log. */
    fun clear() = _logs.clear()
}
