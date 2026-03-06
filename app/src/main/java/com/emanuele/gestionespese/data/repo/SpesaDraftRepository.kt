package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.SpesaDraftDao
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
import kotlinx.coroutines.flow.Flow

class SpesaDraftRepository(
    private val dao: SpesaDraftDao
) {
    fun observeHold(): Flow<List<SpesaDraftEntity>> = dao.observeByStatus("HOLD")
    suspend fun delete(id: Long) { dao.deleteById(id) }
    suspend fun insertTest(entity: SpesaDraftEntity): Long = dao.insert(entity)
}