/**
 * DashboardEntity.kt
 *
 * Entità Room che persiste il layout personalizzato della dashboard per ogni utente.
 * Il layout viene serializzato in JSON e deserializzato da [DashboardRepository].
 */
package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Layout della dashboard di un utente, salvato localmente.
 *
 * @property utente     ID dell'utente, usato come chiave primaria (un layout per utente).
 * @property layoutJson Lista di widget serializzata in JSON (vedi `WidgetConfig`).
 * @property updatedAt  Timestamp Unix dell'ultimo aggiornamento.
 */
@Entity(tableName = "dashboard")
data class DashboardEntity(
    @PrimaryKey val utente: String,
    val layoutJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)
