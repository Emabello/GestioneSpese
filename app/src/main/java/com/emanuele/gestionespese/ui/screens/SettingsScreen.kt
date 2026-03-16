package com.emanuele.gestionespese.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.emanuele.gestionespese.BuildConfig
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

// Log in memoria per la modalità sviluppatore
object DevLogger {
    private const val MAX_LINES = 200
    private val _logs = mutableStateListOf<String>()
    val logs: List<String> get() = _logs

    fun log(tag: String, msg: String) {
        val entry = "[${tag}] $msg"
        android.util.Log.d(tag, msg)
        _logs.add(0, entry) // più recenti in cima
        if (_logs.size > MAX_LINES) _logs.removeLastOrNull()
    }

    fun clear() = _logs.clear()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SpeseViewModel, onNavigateToConfig: () -> Unit) {
    val context  = LocalContext.current
    val app      = context.applicationContext as MyApp
    val userId   = app.currentUserId
    val userName = app.currentUserLabel ?: "—"
    val scope    = rememberCoroutineScope()
    val state    by vm.state.collectAsState()

    var googleLinked      by remember { mutableStateOf(app.currentGoogleLinked) }
    var biometricEnabled  by remember { mutableStateOf(app.biometricEnabled) }
    var isLinking         by remember { mutableStateOf(false) }
    var linkMessage       by remember { mutableStateOf<String?>(null) }
    var linkError         by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // ── Modalità sviluppatore ─────────────────────────────────────────
    // Si attiva premendo 7 volte sull'utente connesso
    var devTapCount      by remember { mutableIntStateOf(0) }
    var devModeEnabled   by remember { mutableStateOf(false) }
    var showDevLogs      by remember { mutableStateOf(false) }
    val devLogs          = DevLogger.logs

    val credentialManager = remember { CredentialManager.create(context) }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Controlla se l'accesso alle notifiche è abilitato
    val notificationAccessEnabled = remember {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        enabledListeners.contains(context.packageName)
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
                val result = credentialManager.getCredential(request = request, context = context as Activity)
                val credential = result.credential
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val googleId   = extractSubFromToken(googleCred.idToken)
                    if (googleId.isBlank()) {
                        linkError = true; linkMessage = "Impossibile leggere l'ID Google"
                        isLinking = false; return@launch
                    }
                    onGoogleId(googleId)
                } else {
                    linkError = true; linkMessage = "Tipo credenziale non supportato"; isLinking = false
                }
            } catch (e: GetCredentialException) {
                linkError = true; linkMessage = "Errore: ${e.message}"; isLinking = false
            } catch (e: Exception) {
                linkError = true; linkMessage = "Errore: ${e.message}"; isLinking = false
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Conferma logout") },
            text  = { Text("Verranno rimossi tutti i dati di sessione, l'impronta digitale e le credenziali salvate.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    app.clearSession()
                    context.startActivity(Intent(context, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }) { Text("Esci", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Annulla") } }
        )
    }

    // Dialog log sviluppatore
    if (showDevLogs) {
        AlertDialog(
            onDismissRequest = { showDevLogs = false },
            modifier = Modifier.fillMaxHeight(0.85f),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Log sviluppatore")
                    TextButton(onClick = { DevLogger.clear() }) {
                        Text("Pulisci", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (devLogs.isEmpty()) {
                        Text(
                            "Nessun log disponibile.\nEsegui operazioni per vedere i log qui.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        devLogs.forEach { line ->
                            val color = when {
                                line.contains("ERROR", ignoreCase = true) -> MaterialTheme.colorScheme.error
                                line.contains("OK", ignoreCase = true)    -> Brand
                                line.contains("NOTIFICA", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text     = line,
                                style    = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color    = color,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevLogs = false }) { Text("Chiudi") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                actions = {
                    IconButton(
                        onClick  = { vm.syncAll() },
                        enabled  = !state.loading && !state.loadingLookups
                    ) {
                        if (state.loading || state.loadingLookups) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color       = Brand
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sincronizza", tint = Brand)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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

            if (state.loading || state.loadingLookups) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Brand)
                Text("Sincronizzazione in corso…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── Card utente — tap 7 volte per modalità sviluppatore ──
            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .clickable {
                            devTapCount++
                            if (devTapCount >= 7) {
                                devModeEnabled = true
                                devTapCount = 0
                            }
                        },
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier         = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape    = MaterialTheme.shapes.medium,
                            color    = Brand.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxSize()
                        ) { }
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = Brand, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        Text("Utente connesso",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(userName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        // Hint tap counter
                        if (devTapCount in 1..6) {
                            Text(
                                "Ancora ${7 - devTapCount} tap per modalità sviluppatore",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (devModeEnabled) {
                            Text(
                                "🛠 Modalità sviluppatore attiva",
                                style = MaterialTheme.typography.labelSmall,
                                color = Brand
                            )
                        }
                    }
                }
            }

            // ── Card notifiche bancarie ───────────────────────────────
            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                shape    = MaterialTheme.shapes.medium,
                                color    = if (notificationAccessEnabled) Brand.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxSize()
                            ) { }
                            Icon(
                                imageVector = if (notificationAccessEnabled)
                                    Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = if (notificationAccessEnabled) Brand
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Notifiche bancarie", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (notificationAccessEnabled)
                                    "Accesso notifiche abilitato — Webank attivo"
                                else
                                    "Accesso notifiche non abilitato",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (notificationAccessEnabled)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (!notificationAccessEnabled) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Abilita accesso notifiche")
                        }
                    }
                }
            }

            // ── Card Google ──────────────────────────────────────────
            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                shape    = MaterialTheme.shapes.medium,
                                color    = if (googleLinked) Brand.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxSize()
                            ) { }
                            Icon(
                                imageVector = if (googleLinked) Icons.Default.CheckCircle else Icons.Default.Link,
                                contentDescription = null,
                                tint = if (googleLinked) Brand else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Account Google", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (googleLinked) "Collegato — puoi accedere con Google" else "Non collegato",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    linkMessage?.let { msg ->
                        Surface(
                            shape    = MaterialTheme.shapes.small,
                            color    = if (linkError) MaterialTheme.colorScheme.errorContainer
                            else Brand.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text     = msg,
                                modifier = Modifier.padding(10.dp),
                                style    = MaterialTheme.typography.bodySmall,
                                color    = if (linkError) MaterialTheme.colorScheme.onErrorContainer else Brand
                            )
                        }
                    }
                    if (isLinking) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Brand)
                    } else {
                        if (!googleLinked) {
                            Button(
                                onClick  = {
                                    launchGoogleSignIn { googleId ->
                                        scope.launch {
                                            try {
                                                if (userId == null) { linkError = true; linkMessage = "Utente non trovato"; isLinking = false; return@launch }
                                                val res = app.api.linkGoogle(LinkGoogleRequest(id = userId, google_id = googleId))
                                                if (res.error == null) {
                                                    googleLinked = true; app.currentGoogleLinked = true
                                                    app.saveSession(app.currentUserLabel ?: "", userId, true)
                                                    linkError = false; linkMessage = "✓ Account Google collegato!"
                                                } else { linkError = true; linkMessage = res.error }
                                            } catch (e: Exception) { linkError = true; linkMessage = "Errore: ${e.message}" }
                                            finally { isLinking = false }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(containerColor = Brand)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Collega account Google")
                            }
                        } else {
                            OutlinedButton(
                                onClick  = {
                                    scope.launch {
                                        isLinking = true
                                        try {
                                            if (userId == null) return@launch
                                            val res = app.api.unlinkGoogle(UnlinkGoogleRequest(id = userId))
                                            if (res.error == null) {
                                                googleLinked = false; app.currentGoogleLinked = false
                                                app.saveSession(app.currentUserLabel ?: "", userId, false)
                                                linkError = false; linkMessage = "Account Google scollegato"
                                            } else { linkError = true; linkMessage = res.error }
                                        } catch (e: Exception) { linkError = true; linkMessage = "Errore: ${e.message}" }
                                        finally { isLinking = false }
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
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                shape    = MaterialTheme.shapes.medium,
                                color    = if (biometricEnabled) Brand.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxSize()
                            ) { }
                            Icon(Icons.Default.Fingerprint, contentDescription = null,
                                tint = if (biometricEnabled) Brand else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Accesso biometrico", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (biometricEnabled) "Abilitato — impronta / viso" else "Disabilitato",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked         = biometricEnabled,
                            onCheckedChange = { enabled -> biometricEnabled = enabled; app.saveBiometricEnabled(enabled) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Brand,
                                checkedTrackColor = Brand.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }

            // ── Card Gestione dati ────────────────────────────────────
            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gestione dati", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Gestisci tipologie, categorie, sottocategorie, conti e combinazioni utente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick  = onNavigateToConfig,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = Brand)
                    ) {
                        Icon(Icons.Default.TableRows, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Apri gestione tabelle")
                    }
                }
            }

            // ── Card Logout ───────────────────────────────────────────
            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sessione",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick  = { showLogoutConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Esci dall'account", style = MaterialTheme.typography.titleMedium) }
                }
            }

            // ── Card Sviluppatore (visibile solo dopo 7 tap) ──────────
            AnimatedVisibility(visible = devModeEnabled) {
                ElevatedCard(
                    modifier  = Modifier.fillMaxWidth(),
                    colors    = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Code, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Modalità sviluppatore",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }

                        Text(
                            "Versione: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                                    "Debug: ${BuildConfig.DEBUG}\n" +
                                    "Utente: ${app.currentUserLabel}\n" +
                                    "userId: ${app.currentUserId}",
                            style      = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color      = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))

                        // Log chiamate API
                        Button(
                            onClick  = { showDevLogs = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Visualizza log (${devLogs.size})")
                        }

                        // Forza sync
                        OutlinedButton(
                            onClick  = {
                                DevLogger.log("DEV", "Sync forzato manualmente")
                                vm.syncAll()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Forza sync completo")
                        }

                        // Notifiche — impostazioni precise
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Impostazioni listener notifiche")
                        }

                        // Disattiva dev mode
                        TextButton(
                            onClick  = { devModeEnabled = false; devTapCount = 0 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disattiva modalità sviluppatore",
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}