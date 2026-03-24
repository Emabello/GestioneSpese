package com.emanuele.gestionespese.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.data.local.entities.ParseRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankProfileDao {

    // ── Profili ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM bank_profile ORDER BY displayName ASC")
    fun getAllProfiles(): Flow<List<BankProfileEntity>>

    @Query("SELECT * FROM bank_profile WHERE isActive = 1")
    suspend fun getActiveProfiles(): List<BankProfileEntity>

    @Query("SELECT * FROM bank_profile WHERE id = :id")
    suspend fun getProfileById(id: Long): BankProfileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfile(profile: BankProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: BankProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: BankProfileEntity)

    // ── Regole ────────────────────────────────────────────────────────────────

    /** Regole ordinate per priority ASC: la regola con priority=0 viene provata prima. */
    @Query("SELECT * FROM parse_rule WHERE bankProfileId = :profileId ORDER BY priority ASC")
    suspend fun getRulesForProfile(profileId: Long): List<ParseRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: ParseRuleEntity): Long

    @Delete
    suspend fun deleteRule(rule: ParseRuleEntity)

    @Query("DELETE FROM parse_rule WHERE bankProfileId = :profileId")
    suspend fun deleteAllRulesForProfile(profileId: Long)
}
