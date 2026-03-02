package com.emanuele.gestionespese.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emanuele.gestionespese.LoginRequest
import com.emanuele.gestionespese.data.remote.SupabaseApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class Login(val api: SupabaseApi) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow() // Lo esponiamo come StateFlow di sola lettura

    fun performLogin(utente: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _isLoading.value = true // Inizia il caricamento
        viewModelScope.launch {
            try {
                val response = api.login(LoginRequest(utente, pass))
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onError("Credenziali errate")
                }
            } catch (e: Exception) {
                onError("Errore rete: ${e.message}")
            } finally {
                _isLoading.value = false // Caricamento finito (successo o errore!)
            }
        }
    }
}