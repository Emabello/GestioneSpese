/**
 * MainActivity.kt
 *
 * Activity principale dell'app, raggiunta dopo autenticazione riuscita.
 * Crea [SpeseRepository] e [SpeseViewModel], imposta il contenuto Compose
 * con [AppNav] e avvia la sincronizzazione iniziale dei dati tramite [SpeseViewModel.syncAll].
 *
 * Se la sessione non è valida (nessun utente loggato), reindirizza a [LoginActivity].
 */
package com.emanuele.gestionespese

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.emanuele.gestionespese.BuildConfig
import com.emanuele.gestionespese.data.repo.SpeseRepository
import com.emanuele.gestionespese.ui.AppNav
import com.emanuele.gestionespese.ui.theme.GestioneSpeseTheme
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel
import com.emanuele.gestionespese.utils.UpdateChecker
import com.emanuele.gestionespese.utils.UpdateInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app         = application as MyApp
        val userLabel   = app.currentUserLabel

        if (userLabel.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val repository = SpeseRepository(
            api       = app.api,
            lookupDao = app.db.lookupDao(),
            spesaDao  = app.db.spesaDao()   // ← aggiunto
        )

        val viewModel: SpeseViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SpeseViewModel(repository, userLabel) as T
            }
        })[SpeseViewModel::class.java]

        val updateState = mutableStateOf<UpdateInfo?>(null)
        val downloadingState = mutableStateOf(false)
        val downloadProgressState = mutableFloatStateOf(0f)

        viewModel.syncAll()

        setContent {
            GestioneSpeseTheme {
                AppNav(vm = viewModel)

                val updateInfo by updateState
                val downloading by downloadingState
                val downloadProgress by downloadProgressState

                if (updateInfo != null && !downloading) {
                    AlertDialog(
                        onDismissRequest = { updateState.value = null },
                        title = { Text("Aggiornamento disponibile") },
                        text = {
                            Text(
                                "È disponibile la versione ${updateInfo!!.latestVersion}.\n" +
                                "Versione attuale: ${BuildConfig.VERSION_NAME}\n\n" +
                                "Vuoi scaricare e installare l'aggiornamento?"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val info = updateInfo!!
                                updateState.value = null
                                downloadingState.value = true
                                lifecycleScope.launch {
                                    UpdateChecker.downloadAndInstall(
                                        context = this@MainActivity,
                                        downloadUrl = info.downloadUrl,
                                        fileName = "GestioneSpese-v${info.latestVersion}.apk",
                                        onProgress = { progress ->
                                            downloadProgressState.floatValue = progress / 100f
                                        }
                                    )
                                    downloadingState.value = false
                                }
                            }) {
                                Text("Scarica aggiornamento")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateState.value = null }) {
                                Text("Più tardi")
                            }
                        }
                    )
                }

                if (downloading) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Download in corso...") },
                        text = {
                            LinearProgressIndicator(progress = { downloadProgress })
                        },
                        confirmButton = {}
                    )
                }
            }
        }


        lifecycleScope.launch {
            val info = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
            if (info?.isUpdateAvailable == true) {
                updateState.value = info
            }
        }
    }
}
