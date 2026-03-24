package com.emanuele.gestionespese.data.repo

import com.emanuele.gestionespese.data.local.BankProfileDao
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.data.local.entities.ParseRuleEntity
import kotlinx.coroutines.flow.Flow

class BankProfileRepository(private val dao: BankProfileDao) {

    /** Flow reattivo di tutti i profili (anche inattivi), ordinati per nome. */
    val allProfiles: Flow<List<BankProfileEntity>> = dao.getAllProfiles()

    suspend fun getProfileById(id: Long): BankProfileEntity? = dao.getProfileById(id)

    suspend fun saveProfile(profile: BankProfileEntity): Long = dao.insertProfile(profile)

    suspend fun updateProfile(profile: BankProfileEntity) = dao.updateProfile(profile)

    suspend fun deleteProfile(profile: BankProfileEntity) = dao.deleteProfile(profile)

    suspend fun getRulesForProfile(profileId: Long): List<ParseRuleEntity> =
        dao.getRulesForProfile(profileId)

    suspend fun upsertRule(rule: ParseRuleEntity): Long = dao.upsertRule(rule)

    suspend fun deleteRule(rule: ParseRuleEntity) = dao.deleteRule(rule)

    /** Sostituisce tutte le regole del profilo con la nuova lista. */
    suspend fun replaceAllRules(profileId: Long, rules: List<ParseRuleEntity>) {
        dao.deleteAllRulesForProfile(profileId)
        rules.forEach { dao.upsertRule(it.copy(bankProfileId = profileId)) }
    }
}
