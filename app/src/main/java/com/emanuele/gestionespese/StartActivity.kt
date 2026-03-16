package com.emanuele.gestionespese

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.fragment.app.FragmentActivity
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.GestioneSpeseTheme

class StartActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MyApp

        // Sessione non presente → vai al login
        if (app.currentUserLabel.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Sessione presente + biometria abilitata → chiedi biometria
        if (app.biometricEnabled) {
            showBiometricPrompt(
                onSuccess = { goToMain() },
                onFallback = { goToLogin() }
            )
        } else {
            // Sessione presente senza biometria → vai diretto
            goToMain()
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onFallback: () -> Unit) {
        val app = application as MyApp

        setContent {
            GestioneSpeseTheme {
                // Schermata di attesa mentre appare il prompt biometrico
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "Bentornato",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            app.currentUserLabel ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(color = Brand, strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onFallback() }) {
                            Text("Usa utenza e password")
                        }
                    }
                }
            }
        }

        val executor = getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Errore o annullato → fallback al login
                    onFallback()
                }
                override fun onAuthenticationFailed() {
                    // Impronta non riconosciuta — il sistema riprova da solo
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Accedi a Gestione Spese")
            .setSubtitle("Usa impronta digitale o riconoscimento viso")
            .setNegativeButtonText("Usa password")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun goToLogin() {
        (application as MyApp).clearSession()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}