package com.emanuele.gestionespese.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.emanuele.gestionespese.BuildConfig
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onListenerConnected() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Listener connesso, notifiche attive: ${activeNotifications.size}")
        activeNotifications.forEach { processNotification(it) }
    }

    override fun onListenerDisconnected() {
        ioScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName != WEBANK_PKG) return

        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val big  = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val content = text.ifBlank { big }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Webank notifica ricevuta, lunghezza contenuto: ${content.length}")
        }

        val parsed = parseWebank(content, sbn.postTime) ?: run {
            if (BuildConfig.DEBUG) Log.w(TAG, "Pattern non trovato nella notifica Webank")
            return
        }

        val entity = SpesaDraftEntity(
            amountCents = parsed.amountCents,
            dateMillis = parsed.dateMillis,
            metodoPagamento = "Webank",
            descrizione = parsed.merchant,
            categoriaId = null,
            sottocategoriaId = null,
            status = "HOLD",
            dedupKey = buildDedupKey(
                sourceLabel = "Webank",
                merchant = parsed.merchant,
                amountCents = parsed.amountCents,
                timeMillis = parsed.dateMillis
            )
        )

        ioScope.launch {
            try {
                val dao = (applicationContext as MyApp).db.spesaDraftDao()
                val id = dao.insertIgnore(entity)
                if (BuildConfig.DEBUG) Log.d(TAG, "Bozza salvata id=$id importo=${parsed.amountCents}c merchant=${parsed.merchant}")
            } catch (t: Throwable) {
                Log.e(TAG, "Errore salvataggio bozza", t)
            }
        }
    }

    companion object {
        private const val TAG = "BankNotificationListener"
        private const val WEBANK_PKG = "com.opentecheng.android.webank"
    }
}