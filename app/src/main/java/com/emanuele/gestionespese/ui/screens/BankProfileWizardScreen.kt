/**
 * BankProfileWizardScreen.kt
 *
 * Wizard guidato in 4 step per configurare un profilo bancario senza scrivere regex:
 *   Step 0 — Seleziona l'app bancaria e dai un nome al profilo.
 *   Step 1 — Ottieni il testo di una notifica (incolla manualmente o cattura live).
 *   Step 2 — Seleziona con il dito le parti del testo (IMPORTO, ESERCENTE).
 *   Step 3 — Anteprima del risultato e salvataggio.
 *
 * Le regex vengono generate automaticamente da [RegexGenerator] in base alle selezioni.
 * Il testo campione e le selezioni vengono salvati in [BankProfileEntity.wizardSampleText]
 * e [BankProfileEntity.wizardSelections] per permettere la modifica futura.
 */
package com.emanuele.gestionespese.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.notifications.BankNotificationListener
import com.emanuele.gestionespese.notifications.CapturedNotification
import com.emanuele.gestionespese.notifications.GenericBankParser
import com.emanuele.gestionespese.ui.components.InstalledAppPickerSheet
import com.emanuele.gestionespese.ui.components.SelectableNotificationText
import com.emanuele.gestionespese.ui.components.SelectionLegend
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.utils.RegexGenerator
import com.emanuele.gestionespese.utils.WizardSelection
import com.emanuele.gestionespese.ui.viewmodel.BankProfileViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val gson = Gson()

// ── Serializzazione selezioni ─────────────────────────────────────────────────

private fun selectionsToJson(selections: List<WizardSelection>): String =
    gson.toJson(selections)

private fun selectionsFromJson(json: String): List<WizardSelection> = try {
    val type = object : TypeToken<List<WizardSelection>>() {}.type
    gson.fromJson(json, type) ?: emptyList()
} catch (e: Exception) { emptyList() }

// ── Screen principale ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankProfileWizardScreen(
    vm: BankProfileViewModel,
    profileId: Long,           // -1L = nuovo profilo
    onBack: () -> Unit,
    onOpenAdvancedEditor: (profileId: Long) -> Unit
) {
    val scope        = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val isEditing    = profileId != -1L

    val selectedProfile by vm.selectedProfile.collectAsState()

    // Carica profilo esistente
    LaunchedEffect(profileId) {
        if (isEditing) vm.selectProfile(profileId)
        else vm.clearSelection()
    }

    // ── Stato wizard ─────────────────────────────────────────────────────────
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    // Step 0
    var selectedPackage by rememberSaveable { mutableStateOf("") }
    var profileName     by rememberSaveable { mutableStateOf("") }
    var showAppPicker   by remember { mutableStateOf(false) }

    // Step 1
    var notificationText by rememberSaveable { mutableStateOf("") }
    var captureActive    by rememberSaveable { mutableStateOf(false) }
    var showCaptureDialog by remember { mutableStateOf(false) }
    val capturedNotifications by BankNotificationListener.capturedNotifications.collectAsState()

    // Step 2 — non usa rememberSaveable perché WizardSelection non è Parcelable
    var wizardSelections by remember { mutableStateOf<List<WizardSelection>>(emptyList()) }

    // Popola dati da profilo esistente
    LaunchedEffect(selectedProfile) {
        selectedProfile?.let { profile ->
            if (selectedPackage.isEmpty()) selectedPackage = profile.packageName
            if (profileName.isEmpty())     profileName     = profile.displayName
            profile.wizardSampleText?.let { t -> if (notificationText.isEmpty()) notificationText = t }
            profile.wizardSelections?.let { j -> if (wizardSelections.isEmpty()) wizardSelections = selectionsFromJson(j) }
        }
    }

    // Popup cattura notifica
    LaunchedEffect(capturedNotifications) {
        if (captureActive && capturedNotifications.isNotEmpty()) showCaptureDialog = true
    }

    // ── Dialog di uscita ──────────────────────────────────────────────────────
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (currentStep > 0) currentStep--
        else showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            icon  = { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Annullare la configurazione?") },
            text  = { Text("I progressi andranno persi.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        BankNotificationListener.stopCapture()
                        vm.clearSelection()
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Esci dal wizard") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Continua") }
            }
        )
    }

    // ── Dialog notifiche catturate ────────────────────────────────────────────
    if (showCaptureDialog && capturedNotifications.isNotEmpty()) {
        CapturedNotificationsDialog(
            notifications = capturedNotifications,
            onDismiss     = { showCaptureDialog = false },
            onSelected    = { text ->
                notificationText  = text
                captureActive     = false
                showCaptureDialog = false
                BankNotificationListener.stopCapture()
                // Se eravamo già allo step 1 passiamo allo step 2
                if (currentStep <= 1) currentStep = 2
            }
        )
    }

    // ── App picker ────────────────────────────────────────────────────────────
    if (showAppPicker) {
        InstalledAppPickerSheet(
            onDismiss     = { showAppPicker = false },
            onAppSelected = { pkg, name ->
                selectedPackage = pkg
                if (profileName.isBlank()) profileName = name
                showAppPicker = false
            }
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Configura banca") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 0) currentStep--
                        else showExitDialog = true
                    }) {
                        Icon(
                            imageVector = if (currentStep > 0) Icons.Default.ArrowBack else Icons.Default.Close,
                            contentDescription = "Indietro"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            BankNotificationListener.stopCapture()
                            onOpenAdvancedEditor(if (isEditing) profileId else -1L)
                        }
                    ) {
                        Text(
                            "Modalità avanzata",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Indicatore step cliccabile in basso
            WizardStepIndicator(
                currentStep = currentStep,
                totalSteps  = 4,
                onStepClick = { step -> if (step < currentStep) currentStep = step }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Contenuto step con animazione slide
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { direction * it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -direction * it } + fadeOut())
                },
                label = "wizard_step"
            ) { step ->
                when (step) {
                    0 -> WizardStep0AppSelection(
                        selectedPackage = selectedPackage,
                        profileName     = profileName,
                        onNameChange    = { profileName = it },
                        onPickApp       = { showAppPicker = true },
                        onNext          = {
                            when {
                                selectedPackage.isBlank() ->
                                    scope.launch { snackbarHost.showSnackbar("Seleziona un'app bancaria") }
                                profileName.isBlank() ->
                                    scope.launch { snackbarHost.showSnackbar("Inserisci un nome per il profilo") }
                                else -> currentStep = 1
                            }
                        }
                    )

                    1 -> WizardStep1NotificationText(
                        text          = notificationText,
                        onTextChange  = { notificationText = it },
                        captureActive = captureActive,
                        onStartCapture = {
                            captureActive = true
                            BankNotificationListener.startCapture(selectedPackage)
                        },
                        onStopCapture = {
                            captureActive = false
                            BankNotificationListener.stopCapture()
                        },
                        onNext = {
                            when {
                                notificationText.isBlank() ->
                                    scope.launch { snackbarHost.showSnackbar("Inserisci o cattura il testo di una notifica") }
                                else -> currentStep = 2
                            }
                        }
                    )

                    2 -> WizardStep2LabelSelection(
                        text           = notificationText,
                        selections     = wizardSelections,
                        onLabelAssigned = { start, end, label ->
                            wizardSelections = wizardSelections.filter { it.label != label } +
                                WizardSelection(start, end, label)
                        },
                        onRemoveLabel  = { label ->
                            wizardSelections = wizardSelections.filter { it.label != label }
                        },
                        onNext = {
                            when {
                                wizardSelections.none { it.label == "IMPORTO" } ->
                                    scope.launch { snackbarHost.showSnackbar("Devi selezionare almeno l'importo") }
                                else -> currentStep = 3
                            }
                        }
                    )

                    3 -> WizardStep3Preview(
                        text       = notificationText,
                        selections = wizardSelections,
                        onBack     = { currentStep = 2 },
                        onSave     = {
                            scope.launch {
                                val rules = RegexGenerator.generateRules(notificationText, wizardSelections)
                                val profile = if (isEditing) {
                                    selectedProfile!!.copy(
                                        displayName      = profileName.trim(),
                                        packageName      = selectedPackage.trim(),
                                        wizardSampleText = notificationText,
                                        wizardSelections = selectionsToJson(wizardSelections)
                                    )
                                } else {
                                    BankProfileEntity(
                                        displayName      = profileName.trim(),
                                        packageName      = selectedPackage.trim(),
                                        wizardSampleText = notificationText,
                                        wizardSelections = selectionsToJson(wizardSelections)
                                    )
                                }
                                val savedId = vm.saveProfileWithRules(profile, rules)
                                if (savedId > 0L || isEditing) {
                                    BankNotificationListener.stopCapture()
                                    vm.clearSelection()
                                    onBack()
                                } else {
                                    snackbarHost.showSnackbar("Errore: questo package è già usato da un altro profilo")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Step 0: Selezione app ─────────────────────────────────────────────────────

@Composable
private fun WizardStep0AppSelection(
    selectedPackage: String,
    profileName: String,
    onNameChange: (String) -> Unit,
    onPickApp: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon  = Icons.Default.PhoneAndroid,
            title = "Seleziona l'app bancaria",
            hint  = "Scegli l'app della tua banca tra quelle installate sul telefono."
        )

        // Picker app
        OutlinedButton(
            onClick  = onPickApp,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (selectedPackage.isBlank()) "Cerca e seleziona app" else "Cambia app")
        }

        if (selectedPackage.isNotBlank()) {
            Surface(
                color  = MaterialTheme.colorScheme.primaryContainer,
                shape  = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            "App selezionata",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            selectedPackage,
                            style      = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Nome profilo
        OutlinedTextField(
            value         = profileName,
            onValueChange = onNameChange,
            label         = { Text("Nome profilo") },
            placeholder   = { Text("es. Webank, ING Direct") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick        = onNext,
            modifier       = Modifier.fillMaxWidth(),
            shape          = RoundedCornerShape(12.dp),
            colors         = ButtonDefaults.buttonColors(containerColor = Brand),
            enabled        = selectedPackage.isNotBlank() && profileName.isNotBlank()
        ) {
            Text("Avanti")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

// ── Step 1: Testo notifica ────────────────────────────────────────────────────

@Composable
private fun WizardStep1NotificationText(
    text: String,
    onTextChange: (String) -> Unit,
    captureActive: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon  = Icons.Default.Notifications,
            title = "Testo della notifica",
            hint  = "Incolla il testo di una notifica bancaria, oppure premi 'Cattura' e fai un pagamento."
        )

        // Cattura live
        if (captureActive) {
            Surface(
                color  = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                shape  = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "In attesa di notifica…",
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Esegui un pagamento con la tua banca",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    TextButton(onClick = onStopCapture) { Text("Annulla") }
                }
            }
        } else {
            OutlinedButton(
                onClick  = onStartCapture,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FiberManualRecord, null,
                    Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Cattura notifica automaticamente")
            }
        }

        HorizontalDivider()

        Text(
            "…oppure incolla il testo manualmente:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value         = text,
            onValueChange = onTextChange,
            label         = { Text("Testo notifica") },
            placeholder   = { Text("Incolla qui il testo completo della notifica bancaria") },
            minLines      = 4,
            maxLines      = 8,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Brand),
            enabled  = text.isNotBlank()
        ) {
            Text("Avanti")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

// ── Step 2: Selezione parti ───────────────────────────────────────────────────

@Composable
private fun WizardStep2LabelSelection(
    text: String,
    selections: List<WizardSelection>,
    onLabelAssigned: (start: Int, end: Int, label: String) -> Unit,
    onRemoveLabel: (label: String) -> Unit,
    onNext: () -> Unit
) {
    val importoSel   = selections.find { it.label == "IMPORTO" }
    val esercSel     = selections.find { it.label == "ESERCENTE" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepHeader(
            icon  = Icons.Default.TouchApp,
            title = "Segna le parti del testo",
            hint  = "Tieni premuto e trascina per selezionare l'importo, poi scegli IMPORTO. Ripeti per l'esercente."
        )

        SelectionLegend()

        // Testo selezionabile (occupa tutto lo spazio disponibile)
        SelectableNotificationText(
            text           = text,
            selections     = selections,
            onLabelAssigned = onLabelAssigned,
            modifier       = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Riepilogo selezioni
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectionChip(
                label     = "IMPORTO",
                selection = importoSel,
                text      = text,
                required  = true,
                onRemove  = { onRemoveLabel("IMPORTO") }
            )
            SelectionChip(
                label     = "ESERCENTE",
                selection = esercSel,
                text      = text,
                required  = false,
                onRemove  = { onRemoveLabel("ESERCENTE") }
            )
        }

        Button(
            onClick  = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Brand),
            enabled  = importoSel != null
        ) {
            Text("Avanti")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SelectionChip(
    label: String,
    selection: WizardSelection?,
    text: String,
    required: Boolean,
    onRemove: () -> Unit
) {
    val chipColor = when (label) {
        "IMPORTO"   -> Color(0xFF3949AB)
        "ESERCENTE" -> Color(0xFF388E3C)
        else        -> MaterialTheme.colorScheme.outline
    }

    Surface(
        color  = if (selection != null) chipColor.copy(alpha = 0.1f)
                 else MaterialTheme.colorScheme.surfaceVariant,
        shape  = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selection != null) chipColor.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (selection != null) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint     = if (selection != null) chipColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text  = "$label${if (required) " *" else " (opzionale)"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selection != null) chipColor
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selection != null) {
                    val selectedText = text.substring(
                        selection.start.coerceIn(0, text.length),
                        selection.end.coerceIn(0, text.length)
                    )
                    Text(
                        text      = "\"$selectedText\"",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = chipColor,
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text  = "Non selezionato",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            if (selection != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Rimuovi",
                        tint     = chipColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ── Step 3: Anteprima ─────────────────────────────────────────────────────────

@Composable
private fun WizardStep3Preview(
    text: String,
    selections: List<WizardSelection>,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    // Esegui parsing con le regole generate (in remember per non ricalcolare ogni recompose)
    val parseResult = remember(text, selections) {
        val rules = RegexGenerator.generateRules(text, selections)
        GenericBankParser.parse(
            text         = text,
            rules        = rules,
            fallbackTime = System.currentTimeMillis(),
            debug        = false
        )
    }

    val importoSel   = selections.find { it.label == "IMPORTO" }
    val esercSel     = selections.find { it.label == "ESERCENTE" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(
            icon  = Icons.Default.Preview,
            title = "Anteprima estrazione",
            hint  = "Controlla che i valori estratti siano corretti prima di salvare."
        )

        // Testo campione con evidenziazione
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text     = text,
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        Text(
            "Risultato estrazione:",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color      = Brand
        )

        // Riga IMPORTO
        PreviewResultRow(
            label   = "Importo",
            value   = if (parseResult != null) {
                val euro = parseResult.amountCents / 100
                val cent = parseResult.amountCents % 100
                "€ $euro,${cent.toString().padStart(2, '0')}"
            } else if (importoSel != null) {
                text.substring(
                    importoSel.start.coerceIn(0, text.length),
                    importoSel.end.coerceIn(0, text.length)
                )
            } else null,
            ok      = parseResult != null
        )

        // Riga ESERCENTE
        PreviewResultRow(
            label   = "Esercente",
            value   = parseResult?.merchant
                ?: esercSel?.let {
                    text.substring(
                        it.start.coerceIn(0, text.length),
                        it.end.coerceIn(0, text.length)
                    )
                },
            ok      = parseResult?.merchant != null,
            optional = true
        )

        // Riga DATA
        PreviewResultRow(
            label    = "Data",
            value    = "Timestamp della notifica",
            ok       = null,   // né ok né errore
            optional = true
        )

        if (parseResult == null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                    Text(
                        "La regex generata non ha trovato un match sul testo campione. " +
                        "Torna indietro e riprova la selezione, oppure usa la modalità avanzata.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp)
            ) { Text("Indietro") }

            Button(
                onClick  = onSave,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salva profilo")
            }
        }
    }
}

@Composable
private fun PreviewResultRow(
    label: String,
    value: String?,
    ok: Boolean?,        // true = verde, false = rosso, null = neutro
    optional: Boolean = false
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        val iconTint = when {
            ok == true  -> Color(0xFF388E3C)
            ok == false -> MaterialTheme.colorScheme.error
            else        -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        }
        Icon(
            imageVector = when {
                ok == true  -> Icons.Default.CheckCircle
                ok == false -> Icons.Default.Cancel
                else        -> Icons.Default.Info
            },
            contentDescription = null,
            tint     = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text  = label + if (optional) " (opzionale)" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text      = value ?: if (optional) "—" else "Non trovato",
                style     = MaterialTheme.typography.bodyMedium,
                fontWeight = if (ok == true) FontWeight.Medium else FontWeight.Normal,
                color     = when {
                    ok == true -> Color(0xFF388E3C)
                    value == null && !optional -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// ── Dialog notifiche catturate ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapturedNotificationsDialog(
    notifications: List<CapturedNotification>,
    onDismiss: () -> Unit,
    onSelected: (text: String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notifiche ricevute (${notifications.size})") },
        text  = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(notifications.reversed()) { notif ->
                    Surface(
                        color     = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape     = RoundedCornerShape(10.dp),
                        modifier  = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(notif.text) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    notif.appName,
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier   = Modifier.weight(1f),
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis
                                )
                                Text(
                                    sdf.format(Date(notif.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                notif.text,
                                style    = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

// ── Componenti condivisi ──────────────────────────────────────────────────────

@Composable
private fun StepHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = Brand, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Barra degli step in fondo: punti colorati, quelli già completati sono cliccabili. */
@Composable
private fun WizardStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    onStepClick: (Int) -> Unit
) {
    Surface(
        color     = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            for (i in 0 until totalSteps) {
                val isCompleted = i < currentStep
                val isCurrent   = i == currentStep

                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent   -> Brand
                                isCompleted -> Brand.copy(alpha = 0.5f)
                                else        -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }
                        )
                        .then(
                            if (isCompleted) Modifier.clickable { onStepClick(i) }
                            else Modifier
                        )
                )

                if (i < totalSteps - 1) {
                    HorizontalDivider(
                        modifier  = Modifier.width(24.dp),
                        thickness = 1.dp,
                        color     = if (isCompleted) Brand.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
