package com.emanuele.gestionespese

import android.app.Application
import androidx.room.Room
import com.emanuele.gestionespese.data.local.AppDatabase

class MyApp : Application() {

    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "gestione_spese.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}