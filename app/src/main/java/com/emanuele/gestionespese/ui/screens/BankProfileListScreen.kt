package com.emanuele.gestionespese.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.viewmodel.BankProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankProfileListScreen(
    vm: BankProfileViewModel,
    onEditProfile: (profileId: Long, useWizard: Boolean) -> Unit,
    onNewProfile: () -> Unit,
    onBack: () -> Unit
) {
    val profiles by vm.profiles.collectAsState()
    var profileToDelete by remember { mutableStateOf<BankProfileEntity?>(null) }

    // ── Dialog conferma eliminazione ─────────────────────────────────────────
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Elimina banca") },
            text  = { Text("Vuoi eliminare \"${profile.displayName}\"? Tutte le regole di parsing verranno cancellate.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteProfile(profile); profileToDelete = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) { Text("Annulla") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Banche configurate") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick            = onNewProfile,
                icon               = { Icon(Icons.Default.Add, null) },
                text               = { Text("Nuova banca") },
                containerColor     = Brand,
                contentColor       = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            // Stato vuoto
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "Nessuna banca configurata",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tocca + per aggiungere la tua banca",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    BankProfileCard(
                        profile       = profile,
                        onToggle      = { vm.toggleActive(profile) },
                        onEdit        = {
                            // Profili creati con il wizard → apre il wizard
                            // Profili creati con regex manuali → apre l'editor avanzato
                            onEditProfile(profile.id, profile.wizardSampleText != null)
                        },
                        onDeleteLong  = { profileToDelete = profile }
                    )
                }
            }
        }
    }
}

@Composable
private fun BankProfileCard(
    profile: BankProfileEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDeleteLong: () -> Unit
) {
    ElevatedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (profile.isActive) Brand else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )

            Column(Modifier.weight(1f)) {
                Text(
                    profile.displayName,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    profile.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bottone elimina
            IconButton(onClick = onDeleteLong) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Elimina",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }

            // Toggle attivo/inattivo
            Switch(
                checked  = profile.isActive,
                onCheckedChange = { onToggle() },
                colors   = SwitchDefaults.colors(
                    checkedThumbColor = Brand,
                    checkedTrackColor = Brand.copy(alpha = 0.35f)
                )
            )
        }
    }
}
