package com.emanuele.gestionespese.data.remote

import com.emanuele.gestionespese.BuildConfig
import com.emanuele.gestionespese.utils.DevLogger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {

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