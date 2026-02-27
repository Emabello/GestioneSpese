package com.emanuele.gestionespese

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.emanuele.gestionespese.data.remote.RetrofitProvider
import com.emanuele.gestionespese.data.remote.SupabaseApi
import com.emanuele.gestionespese.data.repo.SpeseRepository
import com.emanuele.gestionespese.ui.AppNav
import com.emanuele.gestionespese.ui.theme.GestioneSpeseTheme
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val baseUrl = getString(R.string.supabase_url)
        val apiKey = getString(R.string.supabase_anon_key)

        val retrofit = RetrofitProvider.create(baseUrl, apiKey)
        val api = retrofit.create(SupabaseApi::class.java)

        val repository = SpeseRepository(api)
        val viewModel = SpeseViewModel(repository)

        setContent {
            GestioneSpeseTheme {
                AppNav(vm = viewModel)
            }
        }
    }
}