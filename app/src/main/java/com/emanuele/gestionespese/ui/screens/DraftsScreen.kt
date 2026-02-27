package com.emanuele.gestionespese.ui.screens

import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.data.local.SpesaDraftEntity
import com.emanuele.gestionespese.ui.drafts.DraftsViewModel
import com.emanuele.gestionespese.ui.theme.ExpenseContainer
import com.emanuele.gestionespese.ui.theme.OnExpense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    vm: DraftsViewModel,
    onOpenDraft: (SpesaDraftEntity) -> Unit,
    onBack: () -> Unit
) {
    val drafts by vm.drafts.collectAsState()

    // dialog (solo quando NON sei in selection mode)
    var selectedDraft by remember { mutableStateOf<SpesaDraftEntity?>(null) }

    // selection mode
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    fun toggleSelect(id: Long) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "Selezionati: ${selectedIds.size}" else "Bozze Webank"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedIds = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi")
                    }
                },
                actions = {
                    if (!selectionMode) {
                        TextButton(onClick = { vm.insertFakeDraft() }) {
                            Text("Inserisci test")
                        }
                    } else {
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                // cancella tutte le bozze selezionate
                                selectedIds.forEach { id -> vm.delete(id) }
                                // esci da selection mode
                                selectedIds = emptySet()
                                selectionMode = false
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina selezionati")
                        }

                        TextButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Text("Annulla")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->

        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Nessuna bozza da validare")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    val isSelected = selectedIds.contains(draft.id)

                    DraftCard(
                        draft = draft,
                        selectionMode = selectionMode,
                        selected = isSelected,
                        onClick = {
                            if (selectionMode) {
                                toggleSelect(draft.id)
                            } else {
                                selectedDraft = draft
                            }
                        },
                        onLongPress = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds = setOf(draft.id) // SOLO quella premuta
                            } else {
                                toggleSelect(draft.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // 🔴 DIALOG (disabilitato in selection mode)
    if (!selectionMode) {
        selectedDraft?.let { draft ->
            AlertDialog(
                onDismissRequest = { selectedDraft = null },
                title = { Text("Conferma movimento") },
                text = {
                    Text(
                        "Vuoi validare il pagamento di ${
                            draft.amountCents / 100
                        }.${(draft.amountCents % 100).toString().padStart(2, '0')} €?"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedDraft = null
                            onOpenDraft(draft)
                        }
                    ) {
                        Text("Valida")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            vm.delete(draft.id)
                            selectedDraft = null
                        }
                    ) {
                        Text("Rifiuta")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraftCard(
    draft: SpesaDraftEntity,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val amountText = remember(draft.amountCents) {
        val eur = draft.amountCents / 100
        val cent = (draft.amountCents % 100).toString().padStart(2, '0')
        "$eur,$cent €"
    }
    val dateText = remember(draft.dateMillis) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(draft.dateMillis))
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.elevatedCardColors(containerColor = ExpenseContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {

                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onClick() }
                    )
                    Spacer(Modifier.width(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = draft.descrizione,
                            style = MaterialTheme.typography.titleMedium,
                            color = OnExpense,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(6.dp))

                        AssistChip(
                            onClick = { },
                            label = { Text(draft.metodoPagamento) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelMedium,
                            color = OnExpense.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnExpense,
                        textAlign = TextAlign.End,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 88.dp)
                    )
                }
            }
        }
    }
}