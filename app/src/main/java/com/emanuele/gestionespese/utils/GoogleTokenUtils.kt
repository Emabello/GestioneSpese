/**
 * GoogleTokenUtils.kt
 *
 * Utility per il parsing del token JWT Google ID. Estrae il campo `sub`
 * (Subject Identifier) dal payload del token, usato come ID Google univoco
 * per il collegamento dell'account Google all'utente dell'app.
 */
package com.emanuele.gestionespese.utils

import android.util.Base64
import org.json.JSONObject

/**
 * Estrae il campo `sub` (Subject Identifier) dal payload di un Google ID Token JWT.
 *
 * Il token viene decodificato senza verifica della firma (operazione lato client):
 * la validazione reale avviene sul backend.
 *
 * @param idToken Token JWT restituito da Google Sign-In.
 * @return Il `sub` dell'utente Google, o stringa vuota in caso di errore.
 */
fun extractSubFromToken(idToken: String): String {
    return try {
        val parts = idToken.split(".")
        if (parts.size < 2) return ""
        val payload = String(
            Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING)
        )
        JSONObject(payload).getString("sub")
    } catch (e: Exception) {
        ""
    }
}
