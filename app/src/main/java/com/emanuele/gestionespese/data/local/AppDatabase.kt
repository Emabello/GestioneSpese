package com.emanuele.gestionespese.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.emanuele.gestionespese.data.local.entities.*

@Database(
    entities = [
        SpesaDraftEntity::class,
        TipoEntity::class,
        CategoriaEntity::class,
        ContoEntity::class,
        SottoCategoriaEntity::class,
        UtcEntity::class,
        SpesaEntity::class,
        DashboardEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spesaDraftDao(): SpesaDraftDao
    abstract fun lookupDao(): LookupDao
    abstract fun spesaDao(): SpesaDao
    abstract fun dashboardDao(): DashboardDao
}