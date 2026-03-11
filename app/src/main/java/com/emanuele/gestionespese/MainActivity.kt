package com.emanuele.gestionespese

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.emanuele.gestionespese.data.repo.SpeseRepository
import com.emanuele.gestionespese.ui.AppNav
import com.emanuele.gestionespese.ui.theme.GestioneSpeseTheme
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel

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

        setContent {
            GestioneSpeseTheme {
                AppNav(vm = viewModel)
            }
        }

        viewModel.syncAll()
    }
}