package com.emanuele.gestionespese.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppItem(
    val displayName: String,
    val packageName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppPickerSheet(
    onDismiss: () -> Unit,
    onAppSelected: (packageName: String, displayName: String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var query   by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    // Carica lista app su IO (può essere lento)
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { AppItem(pm.getApplicationLabel(it).toString(), it.packageName) }
                .sortedBy { it.displayName.lowercase() }
        }
        loading = false
    }

    val filtered = remember(allApps, query) {
        if (query.isBlank()) allApps
        else allApps.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Text(
                "Seleziona app bancaria",
                style     = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Campo ricerca
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("Cerca per nome...") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(8.dp))

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nessuna app trovata",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier       = Modifier.heightIn(max = 500.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(
                                app     = app,
                                pm      = pm,
                                onClick = { onAppSelected(app.packageName, app.displayName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppItem, pm: PackageManager, onClick: () -> Unit) {
    val iconBitmap = remember(app.packageName) {
        try {
            val drawable = pm.getApplicationIcon(app.packageName)
            drawable.toBitmap()
        } catch (e: Exception) { null }
    }

    Row(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap      = iconBitmap.asImageBitmap(),
                contentDescription = app.displayName,
                modifier    = Modifier.size(40.dp)
            )
        } else {
            Spacer(Modifier.size(40.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(app.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
}

private fun Drawable.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
