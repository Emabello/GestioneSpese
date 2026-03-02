package com.emanuele.gestionespese

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

class StartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // LaunchedEffect serve per eseguire codice asincrono (come l'attesa)
            // in modo sicuro all'interno di Compose
            LaunchedEffect(Unit) {
                // 1. Toast
                Toast.makeText(this@StartActivity, "Benvenuto!", Toast.LENGTH_SHORT).show()

                // 2. Aspetta 2 secondi
                delay(2000)

                // 3. Naviga alla MainActivity
                val intent = Intent(this@StartActivity, LoginActivity::class.java)
                startActivity(intent)
                finish() // Chiudi questa activity
            }
        }
    }
}