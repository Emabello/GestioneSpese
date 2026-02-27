package com.emanuele.gestionespese.di

import android.content.Context
import androidx.room.Room
import com.emanuele.gestionespese.data.local.AppDatabase

object ServiceLocator {
    @Volatile private var db: AppDatabase? = null

    fun db(context: Context): AppDatabase =
        db ?: synchronized(this) {
            db ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gestione_spese.db"
            ).build().also { db = it }
        }
}