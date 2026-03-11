package com.emanuele.gestionespese

import android.app.Application
import androidx.room.Room
import com.emanuele.gestionespese.data.local.AppDatabase
import com.emanuele.gestionespese.data.remote.RetrofitProvider
import com.emanuele.gestionespese.data.remote.SupabaseApi

class MyApp : Application() {

    lateinit var db: AppDatabase
        private set

    lateinit var api: SupabaseApi
        private set

    var currentUserLabel: String? = null
    var currentUserId: Int? = null
    var currentGoogleLinked: Boolean = false

    override fun onCreate() {
        super.onCreate()

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "gestione_spese.db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        val retrofit = RetrofitProvider.create(
            baseUrl = getString(R.string.backend_url),
            apiKey = getString(R.string.backend_api_key)
        )
        api = retrofit.create(SupabaseApi::class.java)
    }
}