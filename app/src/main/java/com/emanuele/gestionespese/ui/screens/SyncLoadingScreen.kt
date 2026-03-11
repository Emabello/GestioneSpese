package com.emanuele.gestionespese.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emanuele.gestionespese.ui.theme.Brand
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.emanuele.gestionespese.R
import androidx.compose.foundation.Image

@Composable
fun SyncLoadingScreen(
    error: String? = null,
    onRetry: () -> Unit = {}
) {
    // Animazione pulsante sul testo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo / iniziali
            val context = LocalContext.current
            val drawable = remember {
                androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
            }

            if (drawable != null) {
                Image(
                    painter = com.google.accompanist.drawablepainter.rememberDrawablePainter(drawable),
                    contentDescription = "Logo",
                    modifier = Modifier.size(80.dp)
                )
            } else {
                // Fallback con le iniziali se il drawable non carica
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Brand),
                    contentAlignment = Alignment.Center
                ) {
                    Text("GS", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
            }

            Text(
                text = "Gestione Spese",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (error == null) {
                // Spinner + testo animato
                CircularProgressIndicator(
                    color = Brand,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Sincronizzazione in corso…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(alpha)
                )
            } else {
                // Errore con retry
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Errore di sincronizzazione",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = Brand)
                        ) {
                            Text("Riprova")
                        }
                    }
                }
            }
        }
    }
}