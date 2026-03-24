package com.emanuele.gestionespese.notifications

/**
 * Notifica catturata durante la modalità "cattura live" del wizard configurazione profilo bancario.
 *
 * I tre campi di testo corrispondono ai rispettivi extra Android della notifica:
 * - [title]   → `android.title`
 * - [text]    → `android.text`
 * - [bigText] → `android.bigText`
 *
 * La UI del wizard mostra ogni campo non-blank come riga selezionabile separata,
 * più un'opzione "COMBINATO" (title + text/bigText) per banche che distribuiscono
 * merchant e importo su campi diversi.
 *
 * @property packageName  Package dell'app che ha inviato la notifica.
 * @property appName      Nome leggibile dell'app (es. "Hype").
 * @property title        Testo del titolo della notifica (`android.title`).
 * @property text         Testo breve della notifica (`android.text`).
 * @property bigText      Testo esteso della notifica (`android.bigText`).
 * @property timestamp    Timestamp Unix (ms) di ricezione della notifica.
 */
data class CapturedNotification(
    val packageName: String,
    val appName:     String,
    val title:       String,
    val text:        String,
    val bigText:     String,
    val timestamp:   Long
)
