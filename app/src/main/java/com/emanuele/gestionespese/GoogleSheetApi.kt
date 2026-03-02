package com.emanuele.gestionespese
import retrofit2.http.Body
import retrofit2.http.POST

interface GoogleSheetApi {
    @POST("https://script.google.com/macros/s/AKfycbxFS-cyDZ_z439zPhpDRyjTJdzMxxLha_gbbt5mdPeMPdRc4ZuZ6G8c5uhKacZxtbeH/exec") // L'endpoint finale del tuo script
    suspend fun login(@Body credenziali: LoginRequest): LoginResponse
}

// Data class per inviare i dati
data class LoginRequest(val utente: String, val pass: String)

// Data class per ricevere la risposta (es. {"status": "ok"})
data class LoginResponse(val status: String, val message: String)