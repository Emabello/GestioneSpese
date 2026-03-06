package com.emanuele.gestionespese.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpesaDraftDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: SpesaDraftEntity): Long
    @Query("SELECT * FROM spesa_draft WHERE status = :status ORDER BY dateMillis DESC")
    fun observeByStatus(status: String = "HOLD"): Flow<List<SpesaDraftEntity>>
    @Query("UPDATE spesa_draft SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String): Int
    @Query("DELETE FROM spesa_draft WHERE id = :id")
    suspend fun deleteById(id: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpesaDraftEntity): Long
}