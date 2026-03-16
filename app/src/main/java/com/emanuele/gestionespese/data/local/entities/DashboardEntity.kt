package com.emanuele.gestionespese.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard")
data class DashboardEntity(
    @PrimaryKey val utente: String,
    val layoutJson: String,         // JSON serializzato della lista WidgetConfig
    val updatedAt: Long = System.currentTimeMillis()
)