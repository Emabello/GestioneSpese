/**
 * SpesaEntity.kt
 *
 * Entità Room che rappresenta una singola spesa/movimento sincronizzata
 * dal backend. Corrisponde alla tabella `spese` nel database locale.
 */
package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Record di una spesa/entrata dell'utente.
 *
 * @property id            ID univoco del movimento (assegnato dal backend).
 * @property utente        Identificativo dell'utente proprietario.
 * @property data          Data del movimento in formato ISO (es. `"2024-03-15"`).
 * @property importo       Importo in euro (positivo = uscita, negativo = entrata secondo il tipo).
 * @property tipo          Tipo di movimento (es. `"Uscita"`, `"Entrata"`).
 * @property tipoMovimento Sotto-tipo opzionale (es. `"Bonifico"`, `"Carta"`).
 * @property conto         Conto corrente di riferimento, o `null` se non specificato.
 * @property categoria     Categoria della spesa (es. `"Alimentari"`), o `null`.
 * @property sottocategoria Sottocategoria (es. `"Supermercato"`), o `null`.
 * @property descrizione   Nota libera dell'utente, o `null`.
 * @property mese          Mese del movimento (1–12), o `null`.
 * @property anno          Anno del movimento, o `null`.
 */
@Entity(tableName = "spese")
data class SpesaEntity(
    @PrimaryKey val id: Int,
    val utente: String,
    val data: String,
    val importo: Double,
    val tipo: String,
    val tipoMovimento: String? = null,
    val conto: String?,
    val contoDestinazione: String? = null,
    val categoria: String?,
    val sottocategoria: String?,
    val descrizione: String?,
    val mese: Int?,
    val anno: Int?
)
