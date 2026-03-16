package com.emanuele.gestionespese.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.emanuele.gestionespese.BuildConfig
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import com.emanuele.gestionespese.ui.screens.DevLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onListenerConnected() {
        val msg = "Listener connesso — notifiche attive: ${activeNotifications.size}"
        DevLogger.log("NOTIFICA", msg)
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
        activeNotifications.forEach { processNotification(it) }
    }

    override fun onListenerDisconnected() {
        DevLogger.log("NOTIFICA", "Listener disconnesso")
        ioScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getCharSequence("android.title")
        val text  = sbn.notification.extras.getCharSequence("android.text")?.take(60)
        DevLogger.log("NOTIFICA", "PKG=${sbn.packageName} | title=$title | text=$text")
        if (BuildConfig.DEBUG) Log.d(TAG, "PKG=${sbn.packageName} title=$title text=$text")
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName != WEBANK_PKG) return

        val extras  = sbn.notification.extras
        val text    = extras.getCharSequence("android.text")?.toString().orEmpty()
        val big     = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val content = text.ifBlank { big }

        DevLogger.log("NOTIFICA", "=== WEBANK NOTIFICA ===")
        DevLogger.log("NOTIFICA", "text: '${text.take(100)}'")
        DevLogger.log("NOTIFICA", "bigText: '${big.take(100)}'")
        DevLogger.log("NOTIFICA", "content usato (${content.length} chars): '${content.take(120)}'")

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "=== CONTENUTO NOTIFICA COMPLETO ===")
            Log.d(TAG, "text: '$text'")
            Log.d(TAG, "bigText: '$big'")
            Log.d(TAG, "content: '$content'")
        }

        val parsed = parseWebank(content, sbn.postTime) ?: run {
            DevLogger.log("NOTIFICA", "⚠ Pattern non trovato — aggiorna regex in WebankParser")
            if (BuildConfig.DEBUG) Log.w(TAG, "Pattern non trovato")
            return
        }

        DevLogger.log("NOTIFICA", "✓ Parsed: amountCents=${parsed.amountCents} merchant='${parsed.merchant}'")

        val entity = SpesaDraftEntity(
            amountCents      = parsed.amountCents,
            dateMillis       = parsed.dateMillis,
            metodoPagamento  = "",
            descrizione      = parsed.merchant,
            categoriaId      = null,
            sottocategoriaId = null,
            status           = "HOLD",
            dedupKey         = buildDedupKey(
                sourceLabel = "Webank",
                merchant    = parsed.merchant,
                amountCents = parsed.amountCents,
                timeMillis  = parsed.dateMillis
            )
        )

        ioScope.launch {
            try {
                val dao = (applicationContext as MyApp).db.spesaDraftDao()
                val id  = dao.insertIgnore(entity)
                val msg = if (id > 0) "✓ Bozza salvata id=$id importo=${parsed.amountCents}c"
                else "↩ Duplicato ignorato (già presente)"
                DevLogger.log("NOTIFICA", msg)
                if (BuildConfig.DEBUG) Log.d(TAG, msg)
            } catch (t: Throwable) {
                DevLogger.log("NOTIFICA", "ERROR salvataggio: ${t.message}")
                Log.e(TAG, "Errore salvataggio bozza", t)
            }
        }
    }

    companion object {
        private const val TAG       = "BankNotificationListener"
        private const val WEBANK_PKG = "com.opentecheng.android.webank"
    }
}