package com.emanuele.gestionespese.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class SupabaseAuthInterceptor(private val apiKey: String) : Interceptor {
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
