/**
 * ApiKeyInterceptor.kt
 *
 * Interceptor OkHttp che aggiunge automaticamente la API key come query parameter
 * e imposta l'header `Content-Type: application/json` su ogni richiesta HTTP
 * verso il backend Google Apps Script.
 */
package com.emanuele.gestionespese.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor che inietta la chiave API e il content type su ogni richiesta.
 *
 * @param apiKey Chiave API del backend (letta da `res/values/secrets.xml`).
 */
class ApiKeyInterceptor(private val apiKey: String) : Interceptor {

    /**
     * Intercetta la richiesta, aggiunge `?key=<apiKey>` all'URL e imposta
     * `Content-Type: application/json`.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter("key", apiKey)
            .build()

        val request = original.newBuilder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .build()

        return chain.proceed(request)
    }
}
