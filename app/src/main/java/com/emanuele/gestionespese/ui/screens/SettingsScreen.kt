package com.emanuele.gestionespese.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.model.LinkGoogleRequest
import com.emanuele.gestionespese.data.model.UnlinkGoogleRequest
import com.emanuele.gestionespese.ui.theme.Brand
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private const val WEB_CLIENT_ID = "1058320885515-4sj57egqao1nr9l8unkbkuso1utggqe2.apps.googleusercontent.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context  = LocalContext.current
    val app      = context.applicationContext as MyApp
    val userId   = app.currentUserId
    val userName = app.currentUserLabel ?: "—"
    val scope    = rememberCoroutineScope()

    var googleLinked by remember { mutableStateOf(app.currentGoogleLinked) }
    var isLoading    by remember { mutableStateOf(false) }
    var message      by remember { mutableStateOf<String?>(null) }
    var isError      by remember { mutableStateOf(false) }

    val credentialManager = remember { CredentialManager.create(context) }

    fun launchGoogleSignIn(onGoogleId: (String) -> Unit) {
        scope.launch {
            isLoading = true
            message   = null
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
                    onGoogleId(googleCred.id)
                } else {
                    isError  = true
                    message  = "Tipo credenziale non supportato"
                    isLoading = false
                }
            } catch (e: GetCredentialException) {
                isError   = true
                message   = "Errore Google Sign-In: ${e.message}"
                isLoading = false
            } catch (e: Exception) {
                isError   = true
                message   = "Errore: ${e.message}"
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- Card utente ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Utente connesso",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(userName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // --- Card Google ---
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (googleLinked) Icons.Default.CheckCircle else Icons.Default.Link,
                            contentDescription = null,
                            tint = if (googleLinked) Brand else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text("Account Google",
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (googleLinked) "Collegato — puoi accedere con Google"
                                else "Non collegato",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    message?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error else Brand
                        )
                    }

                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        if (!googleLinked) {
                            Button(
                                onClick = {
                                    launchGoogleSignIn { googleId ->
                                        scope.launch {
                                            try {
                                                if (userId == null) {
                                                    isError  = true
                                                    message  = "Utente non trovato"
                                                    isLoading = false
                                                    return@launch
                                                }
                                                val res = app.api.linkGoogle(
                                                    LinkGoogleRequest(id = userId, google_id = googleId)
                                                )
                                                if (res.error == null) {
                                                    googleLinked = true
                                                    app.currentGoogleLinked = true
                                                    isError  = false
                                                    message  = "Account Google collegato!"
                                                } else {
                                                    isError = true
                                                    message = res.error
                                                }
                                            } catch (e: Exception) {
                                                isError = true
                                                message = "Errore: ${e.message}"
                                            } finally {
                                                isLoading = false
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
                                        isLoading = true
                                        try {
                                            if (userId == null) return@launch
                                            val res = app.api.unlinkGoogle(
                                                UnlinkGoogleRequest(id = userId)
                                            )
                                            if (res.error == null) {
                                                googleLinked = false
                                                app.currentGoogleLinked = false
                                                isError = false
                                                message = "Account Google scollegato"
                                            } else {
                                                isError = true
                                                message = res.error
                                            }
                                        } catch (e: Exception) {
                                            isError = true
                                            message = "Errore: ${e.message}"
                                        } finally {
                                            isLoading = false
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

            // --- Placeholder altre impostazioni ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Altre impostazioni",
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("In arrivo — categorie, sottocategorie, conti…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}