package com.emanuele.gestionespese

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.theme.GestioneSpeseTheme
import com.emanuele.gestionespese.ui.viewmodel.LoginViewModel
import com.emanuele.gestionespese.utils.extractSubFromToken
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private const val WEB_CLIENT_ID =
    "1058320885515-4sj57egqao1nr9l8unkbkuso1utggqe2.apps.googleusercontent.com"

class LoginActivity : FragmentActivity() {

    // ── BiometricPrompt a livello di classe — stabile attraverso ricreazioni ──
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app               = application as MyApp
        val api               = app.api
        val credentialManager = CredentialManager.create(this)

        val viewModel: LoginViewModel = ViewModelProvider(
            this, object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LoginViewModel(api) as T
                }
            }
        )[LoginViewModel::class.java]

        // ── Inizializza BiometricPrompt UNA SOLA VOLTA in onCreate ────────────
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // Sessione ancora valida (sessionActive = true) → vai a MainActivity
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Annullato o "Usa password" → non fare nulla, resta sulla login
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Errore biometria: $errString",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Non navigare, non toccare la sessione
                }

                override fun onAuthenticationFailed() {
                    // Impronta non riconosciuta — il sistema mostra già il feedback visivo
                }
            }
        )

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Accesso biometrico")
            .setSubtitle("Usa la tua impronta o il Face ID per accedere")
            .setNegativeButtonText("Usa password")
            .build()

        // ── Condizione pulsante biometrico ────────────────────────────────────
        val canShowBiometricButton = app.biometricEnabled &&
                app.sessionActive &&
                BiometricManager.from(this).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                ) == BiometricManager.BIOMETRIC_SUCCESS

        // ── Stati dialog primo accesso ────────────────────────────────────────
        var showBiometricDialog by mutableStateOf(false)
        var pendingApp          by mutableStateOf<MyApp?>(null)

        setContent {
            GestioneSpeseTheme {

                // Dialog biometria primo accesso (dopo login con credenziali/Google)
                if (showBiometricDialog) {
                    AlertDialog(
                        onDismissRequest = { },
                        icon  = { Icon(Icons.Default.Fingerprint, null, tint = Brand) },
                        title = { Text("Accesso rapido") },
                        text  = {
                            Text("Vuoi usare l'impronta digitale o il riconoscimento viso per i prossimi accessi?")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showBiometricDialog = false
                                    pendingApp?.saveBiometricEnabled(true)
                                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Brand)
                            ) { Text("Sì, abilita") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showBiometricDialog = false
                                pendingApp?.saveBiometricEnabled(false)
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            }) { Text("No, grazie") }
                        }
                    )
                }

                LoginScreen(
                    viewModel           = viewModel,
                    showBiometricButton = canShowBiometricButton,
                    onBiometricLogin    = {
                        // Usa il prompt già inizializzato — nessuna ricreazione
                        biometricPrompt.authenticate(promptInfo)
                    },
                    onLoginSuccess = { userLabel, userId, googleLinked ->
                        app.saveSession(userLabel, userId, googleLinked)

                        val canUseBiometric = BiometricManager.from(this).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                        ) == BiometricManager.BIOMETRIC_SUCCESS

                        if (canUseBiometric && !app.biometricAsked) {
                            pendingApp = app
                            showBiometricDialog = true
                        } else {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                    },
                    onGoogleLogin = {
                        lifecycleScope.launch {
                            try {
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(WEB_CLIENT_ID)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val result = credentialManager.getCredential(
                                    request = request,
                                    context = this@LoginActivity
                                )

                                val credential = result.credential
                                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                                    val googleId   = extractSubFromToken(googleCred.idToken)

                                    if (googleId.isBlank()) {
                                        Toast.makeText(
                                            this@LoginActivity,
                                            "Impossibile leggere l'ID Google",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }

                                    viewModel.performGoogleLogin(
                                        googleId  = googleId,
                                        onSuccess = { label, userId, googleLinked ->
                                            app.saveSession(label, userId, googleLinked)

                                            val canUseBiometric = BiometricManager.from(this@LoginActivity)
                                                .canAuthenticate(
                                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                                                ) == BiometricManager.BIOMETRIC_SUCCESS

                                            if (canUseBiometric && !app.biometricAsked) {
                                                pendingApp = app
                                                showBiometricDialog = true
                                            } else {
                                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                                finish()
                                            }
                                        },
                                        onError = { msg ->
                                            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@LoginActivity,
                                    "Errore Google Sign-In: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

// ── LoginScreen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    showBiometricButton: Boolean,
    onBiometricLogin: () -> Unit,
    onLoginSuccess: (userLabel: String, userId: Int, googleLinked: Boolean) -> Unit,
    onGoogleLogin: () -> Unit
) {
    var utenza          by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val isLoading       by viewModel.isLoading.collectAsState()

    var showErrorPopup by remember { mutableStateOf(false) }
    var errorPopupText by remember { mutableStateOf<String?>(null) }

    val canLogin = utenza.isNotBlank() && password.isNotBlank() && !isLoading

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->

        if (showErrorPopup) {
            val clipboard = LocalClipboard.current
            val scope     = rememberCoroutineScope()
            AlertDialog(
                onDismissRequest = { showErrorPopup = false },
                title = { Text("Errore di accesso") },
                text  = {
                    Column {
                        Text(errorPopupText ?: "Errore sconosciuto")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Puoi copiare il dettaglio per analizzarlo.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("errore", errorPopupText ?: ""))
                                )
                            }
                        }) { Text("Copia") }
                        TextButton(onClick = { showErrorPopup = false }) { Text("Chiudi") }
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text      = "Gestione Spese",
                style     = MaterialTheme.typography.headlineLarge,
                color     = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = "Accedi al tuo account",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── Card credenziali ──────────────────────────────────────────────
            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value           = utenza,
                        onValueChange   = { utenza = it },
                        label           = { Text("Utenza") },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier        = Modifier.fillMaxWidth(),
                        colors          = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor      = Brand,
                            focusedLabelColor       = Brand
                        )
                    )

                    OutlinedTextField(
                        value                = password,
                        onValueChange        = { password = it },
                        label                = { Text("Password") },
                        singleLine           = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon         = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector        = if (passwordVisible) Icons.Filled.Visibility
                                    else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Nascondi" else "Mostra",
                                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor      = Brand,
                            focusedLabelColor       = Brand
                        )
                    )

                    Button(
                        onClick  = {
                            viewModel.performLogin(
                                utente    = utenza.trim(),
                                pass      = password,
                                onSuccess = { label, userId, googleLinked ->
                                    onLoginSuccess(label, userId, googleLinked)
                                },
                                onError   = { msg ->
                                    errorPopupText = msg
                                    showErrorPopup = true
                                }
                            )
                        },
                        enabled  = canLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = Brand,
                            disabledContainerColor = Brand.copy(alpha = 0.4f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Entra",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Pulsante biometrico — solo se già configurato e sessione attiva
            AnimatedVisibility(
                visible = showBiometricButton,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.elevatedCardColors(
                        containerColor = Brand.copy(alpha = 0.08f)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBiometricLogin() }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint               = Brand,
                            modifier           = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Accedi con impronta / Face ID",
                            style = MaterialTheme.typography.titleMedium,
                            color = Brand
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (showBiometricButton) 12.dp else 24.dp))

            // ── Divisore ──────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color    = MaterialTheme.colorScheme.outline
                )
                Text(
                    "oppure",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color    = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Card Google ───────────────────────────────────────────────────
            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGoogleLogin() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Image(
                        painter            = painterResource(id = R.drawable.ic_google),
                        contentDescription = null,
                        modifier           = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Continua con Google",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}