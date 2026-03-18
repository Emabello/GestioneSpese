/**
 * LoginViewModel.kt
 *
 * ViewModel per la schermata di login. Gestisce due flussi di autenticazione:
 * 1. **Credenziali** — username e password tramite [SupabaseApi.login].
 * 2. **Google Sign-In** — Google ID token tramite [SupabaseApi.loginGoogle].
 *
 * Il risultato viene comunicato tramite callback (`onSuccess`/`onError`)
 * invece di StateFlow, poiché il login è un'operazione one-shot.
 */
package com.emanuele.gestionespese.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emanuele.gestionespese.data.model.GoogleLoginRequest
import com.emanuele.gestionespese.data.model.LoginRequest
import com.emanuele.gestionespese.data.remote.SupabaseApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel per le operazioni di autenticazione. */
class LoginViewModel(private val api: SupabaseApi) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)

    /** `true` mentre è in corso una richiesta di login. */
    val isLoading = _isLoading.asStateFlow()

    /**
     * Esegue il login con credenziali username/password.
     *
     * @param utente    Username dell'utente.
     * @param pass      Password dell'utente.
     * @param onSuccess Callback invocato con (userLabel, userId, googleLinked) al successo.
     * @param onError   Callback invocato con il messaggio di errore in caso di fallimento.
     */
    fun performLogin(
        utente: String,
        pass: String,
        onSuccess: (userLabel: String, userId: Int, googleLinked: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.login(LoginRequest(utenza = utente, password = pass))
                if (response.error == null && response.data != null) {
                    val data         = response.data
                    val id           = (data["id"] as? Double)?.toInt() ?: 0
                    val utenza       = data["utenza"] as? String ?: utente
                    val label        = "$id - $utenza"
                    val googleLinked = (data["google_id"] as? String)?.isNotBlank() ?: false
                    onSuccess(label, id, googleLinked)
                } else {
                    onError(response.error ?: "Credenziali errate")
                }
            } catch (e: Exception) {
                onError("Errore di rete: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Esegue il login tramite Google Sign-In.
     *
     * @param googleId  Identificativo Google (`sub` estratto dal JWT con [extractSubFromToken]).
     * @param onSuccess Callback invocato con (userLabel, userId, googleLinked=true) al successo.
     * @param onError   Callback invocato con il messaggio di errore in caso di fallimento.
     */
    fun performGoogleLogin(
        googleId: String,
        onSuccess: (userLabel: String, userId: Int, googleLinked: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = api.loginGoogle(
                    GoogleLoginRequest(google_id = googleId)
                )
                if (response.error == null && response.data != null) {
                    val data   = response.data
                    val id     = (data["id"] as? Double)?.toInt() ?: 0
                    val utenza = data["utenza"] as? String ?: googleId
                    val label  = "$id - $utenza"
                    onSuccess(label, id, true)
                } else {
                    onError(response.error ?: "Account Google non collegato")
                }
            } catch (e: Exception) {
                onError("Errore di rete: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}