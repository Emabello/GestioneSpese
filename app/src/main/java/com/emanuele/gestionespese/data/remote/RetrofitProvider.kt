/**
 * RetrofitProvider.kt
 *
 * Factory singleton per la creazione del client Retrofit utilizzato per
 * comunicare con il backend Google Apps Script. Configura:
 * - [ApiKeyInterceptor] per l'autenticazione API key
 * - Un interceptor di logging verso [DevLogger] per il pannello sviluppatore
 * - [okhttp3.logging.HttpLoggingInterceptor] in modalità DEBUG per il body HTTP completo
 */
package com.emanuele.gestionespese.data.remote

import com.emanuele.gestionespese.BuildConfig
import com.emanuele.gestionespese.utils.DevLogger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Factory per [Retrofit], configurato con interceptor di autenticazione e logging. */
object RetrofitProvider {

    /**
     * Crea un'istanza di [Retrofit] pronta all'uso.
     *
     * @param baseUrl URL base del backend (es. `"https://script.google.com/macros/s/.../exec"`).
     * @param apiKey  Chiave API da aggiungere come query parameter.
     * @return Istanza [Retrofit] con [GsonConverterFactory] e gli interceptor configurati.
     */
    fun create(baseUrl: String, apiKey: String): Retrofit {

        // Interceptor che logga in DevLogger (visibile nella modalità sviluppatore)
        val devLogInterceptor = okhttp3.Interceptor { chain ->
            val req  = chain.request()
            val url  = req.url.toString()
                .replace(Regex("key=[^&]+"), "key=***")  // oscura la API key
                .take(100)
            val startMs = System.currentTimeMillis()
            val resp    = chain.proceed(req)
            val elapsed = System.currentTimeMillis() - startMs
            val status  = if (resp.isSuccessful) "✓ ${resp.code}" else "✗ ${resp.code}"
            DevLogger.log("API", "${req.method} $status ${elapsed}ms — $url")
            resp
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(apiKey))
            .addInterceptor(devLogInterceptor)          // ← sempre attivo (API key oscurata)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}