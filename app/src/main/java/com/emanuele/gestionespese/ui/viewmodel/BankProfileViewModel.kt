package com.emanuele.gestionespese.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.data.local.entities.ParseRuleEntity
import com.emanuele.gestionespese.data.repo.BankProfileRepository
import com.emanuele.gestionespese.notifications.GenericBankParser
import com.emanuele.gestionespese.notifications.ParsedNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Risultato del test di parsing nel pannello sviluppatore. */
sealed class TestResult {
    data class Success(val parsed: ParsedNotification) : TestResult()
    data class Failure(val reason: String) : TestResult()
    /** Nessun profilo configurato nel DB — dropdown vuoto. */
    object Empty : TestResult()
}

class BankProfileViewModel(private val repo: BankProfileRepository) : ViewModel() {

    /** Lista reattiva di tutti i profili (anche inattivi), per la schermata lista. */
    val profiles: StateFlow<List<BankProfileEntity>> =
        repo.allProfiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Profilo correntemente in editing (null = nessuno selezionato). */
    private val _selectedProfile = MutableStateFlow<BankProfileEntity?>(null)
    val selectedProfile: StateFlow<BankProfileEntity?> = _selectedProfile

    /** Regole del profilo selezionato. */
    private val _rulesForSelected = MutableStateFlow<List<ParseRuleEntity>>(emptyList())
    val rulesForSelected: StateFlow<List<ParseRuleEntity>> = _rulesForSelected

    /** Risultato dell'ultimo test di parsing. */
    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult

    // ── Selezione profilo ─────────────────────────────────────────────────────

    fun selectProfile(id: Long) = viewModelScope.launch {
        val profile = repo.getProfileById(id)
        _selectedProfile.value = profile
        _rulesForSelected.value = if (profile != null) repo.getRulesForProfile(id) else emptyList()
    }

    fun clearSelection() {
        _selectedProfile.value = null
        _rulesForSelected.value = emptyList()
        _testResult.value = null
    }

    // ── CRUD profili ──────────────────────────────────────────────────────────

    /** Inserisce un nuovo profilo e restituisce l'id generato. */
    suspend fun saveProfile(profile: BankProfileEntity): Long = repo.saveProfile(profile)

    suspend fun updateProfile(profile: BankProfileEntity) {
        repo.updateProfile(profile)
        if (_selectedProfile.value?.id == profile.id) {
            _selectedProfile.value = profile
        }
    }

    fun toggleActive(profile: BankProfileEntity) = viewModelScope.launch {
        repo.updateProfile(profile.copy(isActive = !profile.isActive))
    }

    fun deleteProfile(profile: BankProfileEntity) = viewModelScope.launch {
        repo.deleteProfile(profile)
        if (_selectedProfile.value?.id == profile.id) clearSelection()
    }

    // ── CRUD regole ───────────────────────────────────────────────────────────

    fun upsertRule(rule: ParseRuleEntity) = viewModelScope.launch {
        repo.upsertRule(rule)
        val profileId = _selectedProfile.value?.id ?: return@launch
        _rulesForSelected.value = repo.getRulesForProfile(profileId)
    }

    fun deleteRule(rule: ParseRuleEntity) = viewModelScope.launch {
        repo.deleteRule(rule)
        _rulesForSelected.value = _rulesForSelected.value.filter { it.id != rule.id }
    }

    /** Salva il profilo e sostituisce tutte le sue regole atomicamente. */
    suspend fun saveProfileWithRules(profile: BankProfileEntity, rules: List<ParseRuleEntity>): Long {
        val id = if (profile.id == 0L) {
            repo.saveProfile(profile)
        } else {
            repo.updateProfile(profile)
            profile.id
        }
        if (id > 0) repo.replaceAllRules(id, rules)
        return id
    }

    // ── Test parser ───────────────────────────────────────────────────────────

    /**
     * Testa il parsing del testo su un profilo specifico con le sue regole correnti.
     * Usa [GenericBankParser] con debug=true per loggare ogni regex su DevLogger.
     */
    fun testParsing(notificationText: String, profileId: Long) = viewModelScope.launch {
        val currentProfiles = profiles.value
        if (currentProfiles.isEmpty()) {
            _testResult.value = TestResult.Empty
            return@launch
        }

        val rules = repo.getRulesForProfile(profileId)
        val result = GenericBankParser.parse(
            text         = notificationText,
            rules        = rules,
            fallbackTime = System.currentTimeMillis(),
            debug        = true
        )
        _testResult.update {
            if (result != null) TestResult.Success(result)
            else TestResult.Failure("Nessuna regex ha trovato un match. Controlla i log PARSER nel pannello sviluppatore.")
        }
    }

    fun clearTestResult() { _testResult.value = null }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(repo: BankProfileRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BankProfileViewModel(repo) as T
            }
    }
}
