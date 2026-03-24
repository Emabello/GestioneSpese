/**
 * SettingsScreen.kt
 *
 * Schermata delle impostazioni dell'app. Organizzata in sezioni:
 * - **Account**: info utente, collegamento/scollegamento Google
 * - **Sicurezza**: abilitazione dell'autenticazione biometrica
 * - **Notifiche**: stato del listener per le notifiche bancarie Webank
 * - **Sviluppatore**: log in-memory di [DevLogger], sincronizzazione forzata
 *
 * La sezione sviluppatore è visibile a tutti ma i log sono significativi
 * principalmente in fase di debug.
 */
package com.emanuele.gestionespese.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emanuele.gestionespese.BuildConfig
import com.emanuele.gestionespese.LoginActivity
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.data.model.LinkGoogleRequest
import com.emanuele.gestionespese.data.model.UnlinkGoogleRequest
import com.emanuele.gestionespese.data.repo.BankProfileRepository
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.viewmodel.BankProfileViewModel
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import com.emanuele.gestionespese.ui.viewmodel.TestResult
import com.emanuele.gestionespese.utils.DevLogger
import com.emanuele.gestionespese.utils.extractSubFromToken
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private const val WEB_CLIENT_ID =
    "1058320885515-4sj57egqao1nr9l8unkbkuso1utggqe2.apps.googleusercontent.com"

// ── Composable helper: SettingRow ────────────────────────────────────────────
@Composable
private fun SettingRow(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

// ── Composable helper: SectionHeader ────────────────────────────────────────
@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Brand,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

// ── SettingsScreen ───────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SpeseViewModel,
    onNavigateToConfig: () -> Unit,
    onNavigateToBankProfiles: () -> Unit
) {
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

    // Dev mode
    var devTapCount    by remember { mutableIntStateOf(0) }
    var devModeEnabled by remember { mutableStateOf(app.devModeEnabled) }
    var showDevLogs    by remember { mutableStateOf(false) }
    val devLogs        = DevLogger.logs

    val credentialManager = remember { CredentialManager.create(context) }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val notificationAccessEnabled = remember {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: ""
        enabled.contains(context.packageName)
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

    // ── Dialog logout ────────────────────────────────────────────────────────
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon  = { Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Esci dall'account?") },
            text  = { Text("Verranno rimossi tutti i dati di sessione, l'impronta digitale e le credenziali salvate.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        app.clearSession()
                        context.startActivity(Intent(context, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Esci") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Annulla") }
            }
        )
    }

    // ── Dialog log sviluppatore ──────────────────────────────────────────────
    if (showDevLogs) {
        var selectedLogTag by remember { mutableStateOf<String?>(null) }
        val timeFormatter  = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        val distinctTags   = remember(devLogs) { devLogs.map { it.tag }.distinct().sorted() }
        val filteredLogs   = remember(devLogs, selectedLogTag) {
            if (selectedLogTag == null) devLogs else devLogs.filter { it.tag == selectedLogTag }
        }

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
                    TextButton(onClick = { DevLogger.clear(); selectedLogTag = null }) {
                        Text("Pulisci", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // ── Filtri per tag ───────────────────────────────────────
                    if (distinctTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedLogTag == null,
                                onClick  = { selectedLogTag = null },
                                label    = { Text("Tutti", style = MaterialTheme.typography.labelSmall) }
                            )
                            distinctTags.forEach { tag ->
                                FilterChip(
                                    selected = selectedLogTag == tag,
                                    onClick  = { selectedLogTag = if (selectedLogTag == tag) null else tag },
                                    label    = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    // ── Lista log ────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (filteredLogs.isEmpty()) {
                            Text(
                                "Nessun log disponibile.\nEsegui operazioni per vedere i log qui.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            filteredLogs.forEach { entry ->
                                val color = when {
                                    entry.message.contains("ERROR", ignoreCase = true) -> MaterialTheme.colorScheme.error
                                    entry.message.contains("OK", ignoreCase = true)    -> Brand
                                    entry.tag == "NOTIFICA"                            -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Text(
                                    text  = "${timeFormatter.format(Date(entry.timestamp))}  [${entry.tag}]  ${entry.message}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = color,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevLogs = false }) { Text("Chiudi") }
            }
        )
    }

    // ── Scaffold ─────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Impostazioni", style = MaterialTheme.typography.titleLarge)
                        AnimatedContent(
                            targetState = state.loading || state.loadingLookups,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "subtitle"
                        ) { loading ->
                            Text(
                                if (loading) "Sincronizzazione…" else userName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick  = { vm.syncAll() },
                        enabled  = !state.loading && !state.loadingLookups
                    ) {
                        AnimatedContent(
                            targetState = state.loading || state.loadingLookups,
                            label = "sync_icon"
                        ) { loading ->
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color       = Brand
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sincronizza", tint = Brand)
                            }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // ── Sezione: Profilo ─────────────────────────────────────────────
            // Hero card utente con gradiente e tap counter dev mode
            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(2.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Brand.copy(alpha = 0.12f), Brand.copy(alpha = 0.03f))
                            )
                        )
                        .clickable {
                            devTapCount++
                            if (devTapCount >= 7) {
                                devModeEnabled = true
                                app.saveDevModeEnabled(true)
                                devTapCount = 0
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar circle
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Brand.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                userName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = Brand,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                userName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Badge Google
                                if (googleLinked) {
                                    AssistChip(
                                        onClick = { },
                                        label = { Text("Google", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Default.CheckCircle, null,
                                                Modifier.size(14.dp), tint = Brand)
                                        },
                                        modifier = Modifier.height(24.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Brand.copy(alpha = 0.1f)
                                        )
                                    )
                                }
                                // Dev mode badge
                                if (devModeEnabled) {
                                    AssistChip(
                                        onClick = { },
                                        label = { Text("Dev", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Code, null,
                                                Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.tertiary)
                                        },
                                        modifier = Modifier.height(24.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                }
                            }
                            // Tap counter hint
                            if (devTapCount in 1..6) {
                                Text(
                                    "Ancora ${7 - devTapCount} tap…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Sezione: Sicurezza ───────────────────────────────────────────
            SectionHeader("Sicurezza")

            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(1.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // Google
                    SettingRow(
                        icon            = if (googleLinked) Icons.Default.CheckCircle else Icons.Default.Link,
                        iconTint        = if (googleLinked) Brand else MaterialTheme.colorScheme.onSurfaceVariant,
                        iconBackground  = if (googleLinked) Brand.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        title           = "Account Google",
                        subtitle        = if (googleLinked) "Collegato — accesso rapido attivo"
                        else "Non collegato"
                    )

                    // Feedback collegamento Google
                    linkMessage?.let { msg ->
                        Surface(
                            shape    = RoundedCornerShape(10.dp),
                            color    = if (linkError) MaterialTheme.colorScheme.errorContainer
                            else Brand.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text     = msg,
                                modifier = Modifier.padding(10.dp),
                                style    = MaterialTheme.typography.bodySmall,
                                color    = if (linkError) MaterialTheme.colorScheme.onErrorContainer else Brand
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Bottone link/unlink Google
                    if (isLinking) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            color = Brand
                        )
                    } else {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            if (!googleLinked) {
                                OutlinedButton(
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Link, null, Modifier.size(16.dp))
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
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Scollega account Google")
                                }
                            }
                        }
                    }

                    // Biometria
                    if (biometricAvailable) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant
                        )
                        val switchScale by animateFloatAsState(
                            targetValue = if (biometricEnabled) 1f else 0.9f,
                            label = "switch_scale"
                        )
                        SettingRow(
                            icon           = Icons.Default.Fingerprint,
                            iconTint       = if (biometricEnabled) Brand
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            iconBackground = if (biometricEnabled) Brand.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            title          = "Accesso biometrico",
                            subtitle       = if (biometricEnabled) "Impronta / Face ID attivi"
                            else "Disabilitato",
                            trailing = {
                                Switch(
                                    checked         = biometricEnabled,
                                    onCheckedChange = { enabled ->
                                        biometricEnabled = enabled
                                        app.saveBiometricEnabled(enabled)
                                    },
                                    modifier = Modifier.scale(switchScale),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Brand,
                                        checkedTrackColor = Brand.copy(alpha = 0.35f)
                                    )
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Sezione: Notifiche ───────────────────────────────────────────
            SectionHeader("Notifiche")

            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(1.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingRow(
                        icon           = if (notificationAccessEnabled) Icons.Default.Notifications
                        else Icons.Default.NotificationsOff,
                        iconTint       = if (notificationAccessEnabled) Brand
                        else MaterialTheme.colorScheme.error,
                        iconBackground = if (notificationAccessEnabled) Brand.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.errorContainer,
                        title          = "Notifiche bancarie",
                        subtitle       = if (notificationAccessEnabled)
                            "Webank attivo — ricezione automatica"
                        else "Accesso non abilitato — tocca per attivare",
                        trailing = {
                            if (notificationAccessEnabled) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Brand,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                    AnimatedVisibility(visible = !notificationAccessEnabled) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                                Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Abilita accesso notifiche")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                    // Voce: Banche configurate
                    SettingRow(
                        icon           = Icons.Default.AccountBalance,
                        iconTint       = Brand,
                        iconBackground = Brand.copy(alpha = 0.12f),
                        title          = "Banche configurate",
                        subtitle       = "Gestisci le app e le regex di parsing",
                        modifier       = Modifier.clickable { onNavigateToBankProfiles() },
                        trailing = {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Sezione: App ─────────────────────────────────────────────────
            SectionHeader("App")

            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(1.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SettingRow(
                    icon           = Icons.Default.TableRows,
                    iconTint       = Brand,
                    iconBackground = Brand.copy(alpha = 0.12f),
                    title          = "Gestione tabelle",
                    subtitle       = "Tipologie, categorie, conti e combinazioni",
                    modifier       = Modifier.clickable { onNavigateToConfig() },
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Sezione: Account ─────────────────────────────────────────────
            SectionHeader("Account")

            ElevatedCard(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(20.dp),
                elevation = CardDefaults.elevatedCardElevation(1.dp),
                colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SettingRow(
                    icon           = Icons.Default.Logout,
                    iconTint       = MaterialTheme.colorScheme.error,
                    iconBackground = MaterialTheme.colorScheme.errorContainer,
                    title          = "Esci dall'account",
                    subtitle       = "Rimuove sessione e credenziali salvate",
                    modifier       = Modifier.clickable { showLogoutConfirm = true },
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    }
                )
            }

            // ── Sezione: Sviluppatore ────────────────────────────────────────
            AnimatedVisibility(
                visible = devModeEnabled,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Sviluppatore")

                    ElevatedCard(
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.elevatedCardElevation(2.dp),
                        colors    = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Info build
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})  •  " +
                                            "debug=${BuildConfig.DEBUG}\n" +
                                            "userId=${app.currentUserId}  •  user=${app.currentUserLabel}",
                                    style      = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color      = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier   = Modifier.padding(10.dp)
                                )
                            }

                            // Log
                            Button(
                                onClick  = { showDevLogs = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Icon(Icons.Default.Terminal, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Log (${devLogs.size})")
                            }

                            // Sync forzato
                            OutlinedButton(
                                onClick  = { DevLogger.log("DEV", "Sync forzato"); vm.syncAll() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Forza sync completo")
                            }

                            // Notification listener settings
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
                                Icon(Icons.Default.Notifications, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Listener notifiche")
                            }

                            HorizontalDivider(thickness = 0.5.dp)

                            // ── Parser Tester ────────────────────────────────
                            ParserTesterSection(vm = vm, app = app)

                            // Disattiva
                            TextButton(
                                onClick  = { devModeEnabled = false; app.saveDevModeEnabled(false); devTapCount = 0 },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Disattiva modalità sviluppatore",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Parser Tester (sezione developer) ────────────────────────────────────────

@Composable
private fun ParserTesterSection(vm: SpeseViewModel, app: MyApp) {
    val context = LocalContext.current
    val bankVm: BankProfileViewModel = viewModel(
        factory = BankProfileViewModel.factory(
            BankProfileRepository(app.db.bankProfileDao())
        )
    )

    val profiles   by bankVm.profiles.collectAsState()
    val testResult by bankVm.testResult.collectAsState()

    var selectedProfile by remember { mutableStateOf<BankProfileEntity?>(null) }
    var notifText       by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Aggiorna profilo selezionato se la lista cambia
    LaunchedEffect(profiles) {
        if (selectedProfile == null && profiles.isNotEmpty()) {
            selectedProfile = profiles.first()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "TEST PARSER NOTIFICHE",
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )

        if (profiles.isEmpty()) {
            Text(
                "Configura almeno una banca per testare il parser",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
            )
        } else {
            // Dropdown selezione profilo
            Box {
                OutlinedButton(
                    onClick  = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedProfile?.displayName ?: "Seleziona banca")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded        = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text    = { Text(profile.displayName) },
                            onClick = { selectedProfile = profile; dropdownExpanded = false }
                        )
                    }
                }
            }

            // TextField testo notifica
            OutlinedTextField(
                value         = notifText,
                onValueChange = { notifText = it },
                label         = { Text("Testo notifica") },
                placeholder   = { Text("Incolla qui il testo della notifica...") },
                minLines      = 3,
                maxLines      = 6,
                modifier      = Modifier.fillMaxWidth()
            )

            // Bottone test
            Button(
                onClick = {
                    val p = selectedProfile ?: return@Button
                    bankVm.testParsing(notifText, p.id)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = notifText.isNotBlank() && selectedProfile != null,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Testa")
            }

            // Risultato
            testResult?.let { result ->
                when (result) {
                    is TestResult.Success -> {
                        val p = result.parsed
                        val euro = p.amountCents / 100
                        val cents = p.amountCents % 100
                        val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALY)
                            .format(java.util.Date(p.dateMillis))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("✅ Parsing riuscito", fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.height(4.dp))
                                Text("Importo:   $euro,${"$cents".padStart(2, '0')} €",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Merchant:  ${p.merchant}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Data:      $date",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    is TestResult.Failure -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("❌ Parsing fallito", fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.height(4.dp))
                                Text(result.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    is TestResult.Empty -> {
                        Text(
                            "Nessun profilo bancario configurato",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}