/**
 * BankNotificationListener.kt
 *
 * [NotificationListenerService] che intercetta le notifiche push delle app bancarie
 * configurate e le converte in bozze di movimenti ([SpesaDraftEntity]) salvate nel
 * database locale tramite [SpesaDraftDao.insertIgnore] con deduplicazione SHA-256.
 *
 * Flusso:
 * 1. Il sistema chiama [onNotificationPosted] per ogni nuova notifica.
 * 2. Si caricano i profili attivi dal DB ([BankProfileDao.getActiveProfiles]).
 * 3. Si cerca il profilo con packageName == sbn.packageName.
 * 4. Se trovato, si estrae il testo in base a [BankProfileEntity.contentSource].
 * 5. [GenericBankParser.parse] estrae importo, merchant e data usando le regole del profilo.
 * 6. La bozza viene salvata in Room (ignorata se già presente via dedupKey).
 * 7. Tutti gli eventi vengono loggati in [DevLogger].
 */
package com.emanuele.gestionespese.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.emanuele.gestionespese.BuildConfig
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import com.emanuele.gestionespese.utils.DevLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Job che osserva _capturePackage per scansionare le notifiche esistenti. */
    private var captureObserverJob: Job? = null

    override fun onListenerConnected() {
        val msg = "Listener connesso — notifiche attive: ${activeNotifications.size}"
        DevLogger.log("NOTIFICA", msg)
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
        activeNotifications.forEach { processNotification(it) }

        // Ogni volta che inizia la cattura, scansiona subito le notifiche già presenti
        captureObserverJob?.cancel()
        captureObserverJob = ioScope.launch {
            _capturePackage
                .filterNotNull()
                .distinctUntilChanged()
                .collect { pkg ->
                    activeNotifications
                        ?.filter { it.packageName == pkg }
                        ?.forEach { handleCaptureMode(it) }
                }
        }
    }

    override fun onListenerDisconnected() {
        captureObserverJob?.cancel()
        ioScope.cancel()
        DevLogger.log("NOTIFICA", "Listener disconnesso")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getCharSequence("android.title")
        val text  = sbn.notification.extras.getCharSequence("android.text")?.take(60)
        DevLogger.log("NOTIFICA", "PKG=${sbn.packageName} | title=$title | text=$text")
        if (BuildConfig.DEBUG) Log.d(TAG, "PKG=${sbn.packageName} title=$title text=$text")
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        ioScope.launch {
            try {
                // ── Modalità cattura live (wizard configurazione) ─────────────────
                val capturePkg = _capturePackage.value
                if (capturePkg != null && sbn.packageName == capturePkg) {
                    handleCaptureMode(sbn)
                    return@launch
                }

                val bankDao = (applicationContext as MyApp).db.bankProfileDao()
                val activeProfiles = bankDao.getActiveProfiles()

                // Cerca il profilo corrispondente al package della notifica
                val profile = activeProfiles.find { it.packageName == sbn.packageName }
                if (profile == null) {
                    // Notifica da app non configurata — ignorata silenziosamente
                    return@launch
                }

                DevLogger.log("NOTIFICA", "=== ${profile.displayName.uppercase()} NOTIFICA ===")

                val content = extractContent(sbn, profile)
                DevLogger.log("NOTIFICA", "content (${content.length} chars): '${content.take(120)}'")

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "=== CONTENUTO NOTIFICA [${profile.displayName}] ===")
                    Log.d(TAG, "content: '$content'")
                }

                val rules = bankDao.getRulesForProfile(profile.id)
                val parsed = GenericBankParser.parse(
                    text         = content,
                    rules        = rules,
                    fallbackTime = sbn.postTime,
                    debug        = false
                )

                if (parsed == null) {
                    DevLogger.log("NOTIFICA", "⚠ Pattern non trovato per ${profile.displayName} — verifica le regex nel configuratore")
                    if (BuildConfig.DEBUG) Log.w(TAG, "Pattern non trovato per ${profile.displayName}")
                    return@launch
                }

                DevLogger.log("NOTIFICA", "✓ Parsed: amountCents=${parsed.amountCents} merchant='${parsed.merchant}'")

                val entity = SpesaDraftEntity(
                    amountCents      = parsed.amountCents,
                    dateMillis       = parsed.dateMillis,
                    metodoPagamento  = profile.displayName,
                    descrizione      = parsed.merchant,
                    categoriaId      = null,
                    sottocategoriaId = null,
                    status           = "HOLD",
                    dedupKey         = buildDedupKey(
                        sourceLabel = profile.displayName,
                        merchant    = parsed.merchant,
                        amountCents = parsed.amountCents,
                        timeMillis  = parsed.dateMillis
                    )
                )

                val draftDao = (applicationContext as MyApp).db.spesaDraftDao()
                val id = draftDao.insertIgnore(entity)
                val msg = if (id > 0) "✓ Bozza salvata id=$id importo=${parsed.amountCents}c"
                else "↩ Duplicato ignorato (già presente)"
                DevLogger.log("NOTIFICA", msg)
                if (BuildConfig.DEBUG) Log.d(TAG, msg)

            } catch (t: Throwable) {
                DevLogger.log("NOTIFICA", "ERROR processNotification: ${t.message}")
                Log.e(TAG, "Errore elaborazione notifica", t)
            }
        }
    }

    /**
     * Estrae il testo della notifica in base al campo [BankProfileEntity.contentSource]:
     * - TEXT_OR_BIG: usa android.text, se vuoto usa android.bigText (default Webank)
     * - TEXT: usa solo android.text
     * - BIG_TEXT: usa solo android.bigText
     * - TITLE: usa android.title
     */
    private fun extractContent(sbn: StatusBarNotification, profile: BankProfileEntity): String {
        val extras = sbn.notification.extras
        val title  = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text   = extras.getCharSequence("android.text")?.toString().orEmpty()
        val big    = extras.getCharSequence("android.bigText")?.toString().orEmpty()

        return when (profile.contentSource) {
            "TITLE"          -> title
            "TEXT"           -> text
            "BIG_TEXT"       -> big
            "TITLE_AND_TEXT" -> {
                val body = big.ifBlank { text }
                listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
            }
            else             -> text.ifBlank { big } // TEXT_OR_BIG (default)
        }
    }

    /**
     * Gestisce la notifica in modalità cattura live: salva nel flow [capturedNotifications]
     * senza creare bozze nel database.
     */
    private fun handleCaptureMode(sbn: StatusBarNotification) {
        val extras  = sbn.notification.extras
        val title   = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text    = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()

        if (title.isBlank() && text.isBlank() && bigText.isBlank()) return

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) { sbn.packageName }

        val captured = CapturedNotification(
            packageName = sbn.packageName,
            appName     = appName,
            title       = title,
            text        = text,
            bigText     = bigText,
            timestamp   = sbn.postTime
        )

        _capturedNotifications.update { current -> (current + captured).takeLast(20) }
        DevLogger.log("NOTIFICA", "📥 Catturata per wizard: pkg=${sbn.packageName} title='${title.take(40)}' text='${text.take(40)}'")
    }

    companion object {
        private const val TAG = "BankNotificationListener"

        // ── Capture mode (wizard configurazione profilo bancario) ─────────────
        /** Package da monitorare. null = modalità cattura inattiva. */
        private val _capturePackage = MutableStateFlow<String?>(null)

        /** Lista delle notifiche catturate durante la modalità cattura. */
        private val _capturedNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())

        /** Flow pubblico in sola lettura per osservare le notifiche catturate. */
        val capturedNotifications: StateFlow<List<CapturedNotification>> =
            _capturedNotifications.asStateFlow()

        /** Avvia la modalità cattura: le notifiche del [packageName] vengono accumulate. */
        fun startCapture(packageName: String) {
            _capturedNotifications.value = emptyList()
            _capturePackage.value = packageName
        }

        /** Ferma la modalità cattura e svuota la lista. */
        fun stopCapture() {
            _capturePackage.value = null
            _capturedNotifications.value = emptyList()
        }
    }
}
