package com.emanuele.gestionespese.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import com.emanuele.gestionespese.data.local.entities.TipoEntity
import com.emanuele.gestionespese.data.local.entities.CategoriaEntity
import com.emanuele.gestionespese.data.local.entities.ContoEntity
import com.emanuele.gestionespese.data.local.entities.SottoCategoriaEntity
import com.emanuele.gestionespese.data.local.entities.UtcEntity

@Database(
    entities = [
        SpesaDraftEntity::class,
        TipoEntity::class,
        CategoriaEntity::class,
        ContoEntity::class,
        SottoCategoriaEntity::class,
        UtcEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spesaDraftDao(): SpesaDraftDao
    abstract fun lookupDao(): LookupDao
}

fun sottoKey(categoria: String, sottocategoria: String) =
    categoria.trim().lowercase() + "||" + sottocategoria.trim().lowercase()

fun utcKey(utente: String, tipologia: String, categoria: String, sottocategoria: String) =
    utente.trim().lowercase() + "||" +
            tipologia.trim().lowercase() + "||" +
            categoria.trim().lowercase() + "||" +
            sottocategoria.trim().lowercase()