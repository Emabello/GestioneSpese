package com.emanuele.gestionespese.notifications

/**
 * Notifica catturata durante la modalità "cattura live" del wizard configurazione profilo bancario.
 *
 * @property packageName  Package dell'app che ha inviato la notifica (es. "com.webank.android").
 * @property appName      Nome leggibile dell'app (es. "Webank").
 * @property text         Testo della notifica (android.text o android.bigText).
 * @property timestamp    Timestamp Unix (ms) di ricezione della notifica.
 */
data class CapturedNotification(
    val packageName: String,
    val appName: String,
    val text: String,
    val timestamp: Long
)
