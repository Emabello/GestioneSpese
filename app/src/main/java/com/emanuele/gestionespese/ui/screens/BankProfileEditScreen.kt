package com.emanuele.gestionespese.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.data.local.entities.ParseRuleEntity
import com.emanuele.gestionespese.ui.components.InstalledAppPickerSheet
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.viewmodel.BankProfileViewModel
import kotlinx.coroutines.launch

private val FIELDS = listOf("AMOUNT", "MERCHANT", "DATE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankProfileEditScreen(
    vm: BankProfileViewModel,
    profileId: Long,          // -1L = nuovo profilo
    onBack: () -> Unit
) {
    val isNew = profileId == -1L
    val selectedProfile by vm.selectedProfile.collectAsState()
    val existingRules   by vm.rulesForSelected.collectAsState()
    val scope           = rememberCoroutineScope()
    val snackbarHost    = remember { SnackbarHostState() }

    // Carica profilo se non è nuovo
    LaunchedEffect(profileId) {
        if (!isNew) vm.selectProfile(profileId)
    }

    // Stato locale form
    var displayName   by remember(selectedProfile) { mutableStateOf(selectedProfile?.displayName ?: "") }
    var packageName   by remember(selectedProfile) { mutableStateOf(selectedProfile?.packageName ?: "") }
    var contentSource by remember(selectedProfile) { mutableStateOf(selectedProfile?.contentSource ?: "TEXT_OR_BIG") }

    // Copia editabile delle regole
    var rules by remember(existingRules) {
        mutableStateOf(existingRules.toMutableList() as List<ParseRuleEntity>)
    }

    var showAppPicker       by remember { mutableStateOf(false) }
    var showContentSourceMenu by remember { mutableStateOf(false) }

    // ── App picker ────────────────────────────────────────────────────────────
    if (showAppPicker) {
        InstalledAppPickerSheet(
            onDismiss = { showAppPicker = false },
            onAppSelected = { pkg, name ->
                packageName = pkg
                displayName = displayName.ifBlank { name }
                showAppPicker = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nuova banca" else "Modifica banca") },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.clearSelection()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Indietro")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                // Validazione base
                                if (displayName.isBlank()) {
                                    snackbarHost.showSnackbar("Il nome non può essere vuoto")
                                    return@launch
                                }
                                if (packageName.isBlank()) {
                                    snackbarHost.showSnackbar("Seleziona un'app bancaria")
                                    return@launch
                                }
                                // Validazione regex
                                for (rule in rules) {
                                    try { Regex(rule.regex) }
                                    catch (e: Exception) {
                                        snackbarHost.showSnackbar(
                                            "Regex ${rule.field} non valida: ${e.message?.take(80)}"
                                        )
                                        return@launch
                                    }
                                }
                                // Salvataggio
                                val profile = if (isNew) {
                                    BankProfileEntity(
                                        displayName   = displayName.trim(),
                                        packageName   = packageName.trim(),
                                        isActive      = true,
                                        contentSource = contentSource
                                    )
                                } else {
                                    selectedProfile!!.copy(
                                        displayName   = displayName.trim(),
                                        packageName   = packageName.trim(),
                                        contentSource = contentSource
                                    )
                                }
                                val savedId = vm.saveProfileWithRules(profile, rules)
                                if (savedId > 0 || !isNew) {
                                    vm.clearSelection()
                                    onBack()
                                } else {
                                    snackbarHost.showSnackbar("Errore: package già usato da un altro profilo")
                                }
                            }
                        }
                    ) {
                        Text("Salva", color = Brand, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Dati profilo ─────────────────────────────────────────────────
            item {
                Text("PROFILO BANCA", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = Brand,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            item {
                OutlinedTextField(
                    value         = displayName,
                    onValueChange = { displayName = it },
                    label         = { Text("Nome visualizzato") },
                    placeholder   = { Text("es. Webank, ING Direct") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedButton(
                    onClick  = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (packageName.isBlank()) "Seleziona app bancaria" else "Cambia app")
                }
                if (packageName.isNotBlank()) {
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }

            // Content source picker
            item {
                Box {
                    OutlinedButton(
                        onClick  = { showContentSourceMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Code, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sorgente testo: $contentSource")
                    }
                    DropdownMenu(
                        expanded        = showContentSourceMenu,
                        onDismissRequest = { showContentSourceMenu = false }
                    ) {
                        listOf(
                            "TEXT_OR_BIG" to "Testo o BigText (default)",
                            "TEXT"        to "Solo testo breve",
                            "BIG_TEXT"    to "Solo testo espanso",
                            "TITLE"       to "Solo titolo"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { contentSource = value; showContentSourceMenu = false },
                                leadingIcon = if (contentSource == value) ({
                                    Icon(Icons.Default.Check, null, tint = Brand)
                                }) else null
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── Regole di parsing ─────────────────────────────────────────────
            item {
                Text("REGOLE DI PARSING", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = Brand,
                    modifier = Modifier.padding(bottom = 4.dp))
                Text(
                    "AMOUNT: 2 gruppi (euro)(centesimi) o 1 gruppo decimale  •  DATE: 5 gruppi (dd)(MM)(yyyy)(HH)(mm)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Una sezione per ogni field type
            FIELDS.forEach { fieldType ->
                val fieldRules = rules.filter { it.field == fieldType }

                item(key = "header_$fieldType") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fieldType,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val nextPriority = (fieldRules.maxOfOrNull { it.priority } ?: -1) + 1
                                rules = rules + ParseRuleEntity(
                                    bankProfileId = profileId.coerceAtLeast(0),
                                    field         = fieldType,
                                    regex         = "",
                                    groupIndex    = 1,
                                    priority      = nextPriority,
                                    description   = ""
                                )
                            }
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Aggiungi")
                        }
                    }
                }

                itemsIndexed(
                    items = fieldRules,
                    key   = { _, rule -> "${rule.field}_${rule.id}_${rule.priority}" }
                ) { _, rule ->
                    RuleCard(
                        rule = rule,
                        onUpdate = { updated ->
                            rules = rules.map { if (it === rule) updated else it }
                        },
                        onDelete = {
                            rules = rules.filter { it !== rule }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun RuleCard(
    rule: ParseRuleEntity,
    onUpdate: (ParseRuleEntity) -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Regex
            OutlinedTextField(
                value         = rule.regex,
                onValueChange = { onUpdate(rule.copy(regex = it)) },
                label         = { Text("Regex") },
                placeholder   = { Text("es. di\\s*-?(\\d+)[,\\.](\\d{2})\\s*EURO") },
                minLines      = 2,
                maxLines      = 4,
                textStyle     = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier      = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Group index
                OutlinedTextField(
                    value         = rule.groupIndex.toString(),
                    onValueChange = { onUpdate(rule.copy(groupIndex = it.toIntOrNull() ?: 1)) },
                    label         = { Text("Gruppo") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.width(90.dp)
                )
                // Priority
                OutlinedTextField(
                    value         = rule.priority.toString(),
                    onValueChange = { onUpdate(rule.copy(priority = it.toIntOrNull() ?: 0)) },
                    label         = { Text("Priorità") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.width(90.dp)
                )
                Spacer(Modifier.weight(1f))
                // Elimina
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Elimina regola",
                        tint = MaterialTheme.colorScheme.error)
                }
            }

            // Description (opzionale)
            OutlinedTextField(
                value         = rule.description,
                onValueChange = { onUpdate(rule.copy(description = it)) },
                label         = { Text("Nota (opzionale)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
        }
    }
}
