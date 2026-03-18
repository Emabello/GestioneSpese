/**
 * DraftsScreen.kt
 *
 * Schermata delle bozze di movimenti bancari catturate automaticamente dalle
 * notifiche push di Webank. Permette di visualizzare, selezionare e eliminare
 * le bozze in attesa di validazione manuale.
 *
 * Supporta la selezione multipla tramite long-press e un pulsante di eliminazione
 * collettiva. Include anche un pulsante di test per inserire bozze fittizie in debug.
 */
package com.emanuele.gestionespese.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.BuildConfig
import com.emanuele.gestionespese.data.local.entities.SpesaDraftEntity
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
    var selectedDraft by remember { mutableStateOf<SpesaDraftEntity?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    fun toggleSelect(id: Long) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifiche") },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                selectedIds.forEach { id -> vm.delete(id) }
                                selectedIds = emptySet()
                                selectionMode = false
                            }
                        ) { Icon(Icons.Default.Delete, contentDescription = "Elimina selezionati") }
                        TextButton(onClick = { selectionMode = false; selectedIds = emptySet() }) { Text("Annulla") }
                        TextButton(onClick = { vm.insertFakeDraft() }) { Text("Test") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (drafts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nessuna bozza da validare")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(drafts, key = { it.id }) { draft ->
                    DraftCard(
                        draft = draft,
                        selectionMode = selectionMode,
                        selected = selectedIds.contains(draft.id),
                        onClick = { if (selectionMode) toggleSelect(draft.id) else selectedDraft = draft },
                        onLongPress = {
                            if (!selectionMode) { selectionMode = true; selectedIds = setOf(draft.id) }
                            else toggleSelect(draft.id)
                        }
                    )
                }
            }
        }
    }

    if (!selectionMode) {
        selectedDraft?.let { draft ->
            AlertDialog(
                onDismissRequest = { selectedDraft = null },
                title = { Text("Conferma movimento") },
                text = {
                    val eur = draft.amountCents / 100
                    val cent = (draft.amountCents % 100).toString().padStart(2, '0')
                    Text("Vuoi validare il pagamento di $eur,$cent €?")
                },
                confirmButton = {
                    TextButton(onClick = { selectedDraft = null; onOpenDraft(draft) }) { Text("Valida") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.delete(draft.id); selectedDraft = null }) { Text("Rifiuta") }
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
        "${draft.amountCents / 100},${(draft.amountCents % 100).toString().padStart(2, '0')} €"
    }
    val dateText = remember(draft.dateMillis) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(draft.dateMillis))
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongPress),
        colors = CardDefaults.elevatedCardColors(containerColor = ExpenseContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(10.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(draft.descrizione, style = MaterialTheme.typography.titleMedium, color = OnExpense,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    AssistChip(onClick = { }, label = { Text(draft.metodoPagamento) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface))
                    Spacer(Modifier.height(6.dp))
                    Text(dateText, style = MaterialTheme.typography.labelMedium, color = OnExpense.copy(alpha = 0.80f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Text(amountText, style = MaterialTheme.typography.headlineSmall, color = OnExpense,
                    textAlign = TextAlign.End, softWrap = false, maxLines = 1,
                    modifier = Modifier.widthIn(min = 88.dp))
            }
        }
    }
}