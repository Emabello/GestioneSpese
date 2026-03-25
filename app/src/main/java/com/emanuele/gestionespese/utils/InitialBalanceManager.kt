package com.emanuele.gestionespese.utils

import android.content.Context

/**
 * Gestisce i saldi iniziali dei conti correnti.
 *
 * I valori sono persistiti su SharedPreferences dedicate (`initial_balances`)
 * per sopravvivere a migrazioni distruttive del database Room e agli
 * aggiornamenti dell'applicazione.
 */
object InitialBalanceManager {

    private const val PREFS_NAME = "initial_balances"

    /** Restituisce il saldo iniziale per un conto, o `0.0` se non impostato. */
    fun getBalance(context: Context, contoName: String): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(contoName, null)?.toDoubleOrNull() ?: 0.0
    }

    /** Imposta il saldo iniziale per un conto. */
    fun setBalance(context: Context, contoName: String, amount: Double) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(contoName, amount.toString()).apply()
    }

    /** Restituisce tutti i saldi iniziali configurati. */
    fun getAllBalances(context: Context): Map<String, Double> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            val amount = (value as? String)?.toDoubleOrNull() ?: return@mapNotNull null
            key to amount
        }.toMap()
    }
}
