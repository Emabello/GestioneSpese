package com.emanuele.gestionespese.utils

import android.util.Base64
import org.json.JSONObject

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