package com.emanuele.gestionespese.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SpesaDraftEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spesaDraftDao(): SpesaDraftDao
}