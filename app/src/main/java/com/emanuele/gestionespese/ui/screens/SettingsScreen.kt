package com.emanuele.gestionespese.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.emanuele.gestionespese.LoginActivity
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.model.LinkGoogleRequest
import com.emanuele.gestionespese.data.model.UnlinkGoogleRequest
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import com.emanuele.gestionespese.utils.extractSubFromToken
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private const val WEB_CLIENT_ID =
    "1058320885515-4sj57egqao1nr9l8unkbkuso1utggqe2.apps.googleusercontent.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SpeseViewModel) {
    val context  = LocalContext.current
    val app      = context.applicationContext as MyApp
    val userId   = app.currentUserId
    val userName = app.currentUserLabel ?: "—"
    val scope    = rememberCoroutineScope()

    val state by vm.state.collectAsState()

    var googleLinked     by remember { mutableStateOf(app.currentGoogleLinked) }
    var biometricEnabled by remember { mutableStateOf(app.biometricEnabled) }
    var isLinking        by remember { mutableStateOf(false) }
    var linkMessage      by remember { mutableStateOf<String?>(null) }
    var linkError        by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val credentialManager = remember { CredentialManager.create(context) }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun launchGoogleSignIn(onGoogleId: (String) -> Unit) {
        scope.launch {
            isLinking   = true
            linkMessage = null
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
                    context = context as Activity
                )
                val credential = result.credential
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val googleId   = extractSubFromToken(googleCred.idToken)
                    if (googleId.isBlank()) {
                        linkError   = true
                        linkMessage = "Impossibile leggere l'ID Google"
                        isLinking   = false
                        return@launch
                    }
                    onGoogleId(googleId)
                } else {
                    linkError   = true
                    linkMessage = "Tipo credenziale non supportato"
                    isLinking   = false
                }
            } catch (e: GetCredentialException) {
                linkError   = true
                linkMessage = "Errore: ${e.message}"
                isLinking   = false
            } catch (e: Exception) {
                linkError   = true
                linkMessage = "Errore: ${e.message}"
                isLinking   = false
            }
        }
    }

    // Dialog logout
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Conferma logout") },
            text = {
                Text("Verranno rimossi tutti i dati di sessione, l'impronta digitale e le credenziali salvate. Dovrai accedere di nuovo.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    app.clearSession()
                    val intent = Intent(context, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                }) {
                    Text("Esci", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Annulla") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                actions = {
                    // Bottone sync forzato
                    IconButton(
                        onClick = { vm.syncAll() },
                        enabled = !state.loading && !state.loadingLookups
                    ) {
                        if (state.loading || state.loadingLookups) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Brand
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Sincronizza",
                                tint = Brand
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Barra sync in corso
            if (state.loading || state.loadingLookups) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Brand
                )
                Text(
                    "Sincronizzazione in corso…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Card utente ──────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = Brand.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxSize()
                        ) { }
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Brand,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            "Utente connesso",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            userName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── Card Google ──────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (googleLinked) Brand.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxSize()
                            ) { }
                            Icon(
                                imageVector = if (googleLinked) Icons.Default.CheckCircle
                                else Icons.Default.Link,
                                contentDescription = null,
                                tint = if (googleLinked) Brand
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Account Google",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (googleLinked) "Collegato — puoi accedere con Google"
                                else "Non collegato",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    linkMessage?.let { msg ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (linkError) MaterialTheme.colorScheme.errorContainer
                            else Brand.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (linkError) MaterialTheme.colorScheme.onErrorContainer
                                else Brand
                            )
                        }
                    }

                    if (isLinking) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Brand
                        )
                    } else {
                        if (!googleLinked) {
                            Button(
                                onClick = {
                                    launchGoogleSignIn { googleId ->
                                        scope.launch {
                                            try {
                                                if (userId == null) {
                                                    linkError   = true
                                                    linkMessage = "Utente non trovato"
                                                    isLinking   = false
                                                    return@launch
                                                }
                                                val res = app.api.linkGoogle(
                                                    LinkGoogleRequest(id = userId, google_id = googleId)
                                                )
                                                if (res.error == null) {
                                                    googleLinked = true
                                                    app.currentGoogleLinked = true
                                                    app.saveSession(app.currentUserLabel ?: "", userId, true)
                                                    linkError   = false
                                                    linkMessage = "✓ Account Google collegato!"
                                                } else {
                                                    linkError   = true
                                                    linkMessage = res.error
                                                }
                                            } catch (e: Exception) {
                                                linkError   = true
                                                linkMessage = "Errore: ${e.message}"
                                            } finally {
                                                isLinking = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Brand)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Collega account Google")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLinking = true
                                        try {
                                            if (userId == null) return@launch
                                            val res = app.api.unlinkGoogle(UnlinkGoogleRequest(id = userId))
                                            if (res.error == null) {
                                                googleLinked = false
                                                app.currentGoogleLinked = false
                                                app.saveSession(app.currentUserLabel ?: "", userId, false)
                                                linkError   = false
                                                linkMessage = "Account Google scollegato"
                                            } else {
                                                linkError   = true
                                                linkMessage = res.error
                                            }
                                        } catch (e: Exception) {
                                            linkError   = true
                                            linkMessage = "Errore: ${e.message}"
                                        } finally {
                                            isLinking = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Scollega account Google")
                            }
                        }
                    }
                }
            }

            // ── Card Biometria ───────────────────────────────────────
            if (biometricAvailable) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (biometricEnabled) Brand.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxSize()
                            ) { }
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (biometricEnabled) Brand
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Accesso biometrico",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (biometricEnabled) "Abilitato — impronta / viso"
                                else "Disabilitato",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { enabled ->
                                biometricEnabled = enabled
                                app.saveBiometricEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Brand,
                                checkedTrackColor = Brand.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }

            // ── Card Altre impostazioni ──────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Altre impostazioni",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "In arrivo — categorie, sottocategorie, conti…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Card Logout ──────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Sessione",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showLogoutConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            "Esci dall'account",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}