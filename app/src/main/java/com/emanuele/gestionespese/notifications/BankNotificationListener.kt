package com.emanuele.gestionespese.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import com.emanuele.gestionespese.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        Log.d("BANK_NOTIF", "✅ Listener CONNECTED")

        val current = activeNotifications
        Log.d("BANK_NOTIF", "Active notifications count=${current.size}")

        current.forEach { sbn ->
            processNotification(sbn)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
    }
    private fun processNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        Log.d("BANK_NOTIF", "PROCESSING PKG=$pkg")

        // ✅ SOLO WEBANK
        if (pkg != "com.opentecheng.android.webank") return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text  = extras.getCharSequence("android.text")?.toString().orEmpty()
        val big   = extras.getCharSequence("android.bigText")?.toString().orEmpty()

        // Fallback: a volte android.text è vuoto ma bigText c’è
        val content = when {
            text.isNotBlank() -> text
            big.isNotBlank() -> big
            else -> ""
        }

        Log.d("BANK_NOTIF", "WEBANK title=$title")
        Log.d("BANK_NOTIF", "WEBANK contentLen=${content.length}")
        Log.d("BANK_NOTIF", "WEBANK content=${content.take(220)}")

        val parsed = parseWebank(content, sbn.postTime)
        if (parsed == null) {
            Log.w("BANK_NOTIF", "WEBANK parseWebank() = null (pattern non matchato)")
            return
        }

        val dedupKey = buildDedupKey(
            sourceLabel = "Webank",
            merchant = parsed.merchant,
            amountCents = parsed.amountCents,
            timeMillis = parsed.dateMillis
        )

        val entity = SpesaDraftEntity(
            amountCents = parsed.amountCents,
            dateMillis = parsed.dateMillis,
            metodoPagamento = "Webank",
            descrizione = parsed.merchant,
            categoriaId = null,
            sottocategoriaId = null,
            status = "HOLD",
            dedupKey = dedupKey
        )

        ioScope.launch {
            try {
                val dao = ServiceLocator.db(applicationContext).spesaDraftDao()
                val res = dao.insertIgnore(entity)
                Log.d("BANK_NOTIF", "✅ WEBANK DRAFT SAVED id=$res amount=${parsed.amountCents} descr=${parsed.merchant}")
            } catch (t: Throwable) {
                Log.e("BANK_NOTIF", "❌ WEBANK insert failed", t)
            }
        }
    }
}