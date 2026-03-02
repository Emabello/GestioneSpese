package com.emanuele.gestionespese

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emanuele.gestionespese.data.remote.RetrofitProvider
import com.emanuele.gestionespese.data.remote.SupabaseApi
import com.emanuele.gestionespese.ui.viewmodel.Login

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. Inizializza l'API (come facevi prima)
        val baseUrl = getString(R.string.supabase_url)
        val apiKey = getString(R.string.supabase_anon_key)
        val retrofit = RetrofitProvider.create(baseUrl, apiKey)
        val api = retrofit.create(SupabaseApi::class.java)

        // 2. Crea la Factory che sa come passare 'api' al tuo ViewModel
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(Login::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return Login(api) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }

        // 3. Ottieni il ViewModel usando la factory
        val viewModel: Login = ViewModelProvider(this, factory)[Login::class.java]

        setContent {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                },
                onGoogleLogin = {
                    // Qui chiamerai la logica per il Google Sign-In
                    Toast.makeText(this, "Avvio Google Sign-In...", Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        }
    }
}

@Composable
fun LoginScreen(viewModel: Login, onLoginSuccess: () -> Unit, onGoogleLogin: () -> Unit) {
    val context = LocalContext.current // Questo recupera il contesto corretto
    var utente by remember { mutableStateOf("") } //Si può inserire direttamente il valore del campo
    var password by remember { mutableStateOf("") } //Si può inserire direttamente la password
    // Aggiungi questo nel tuo Composable
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = utente,
            onValueChange = { utente = it },
            label = { Text("Email o Utenza") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            // Questa riga cambia la visualizzazione in base allo stato
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            // Aggiungiamo l'icona alla fine del campo
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Nascondi password" else "Mostra password"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

//        val scope = rememberCoroutineScope() // Crea un ambito per le operazioni asincrone
        val isLoading by viewModel.isLoading.collectAsState()
        Button(
            onClick = {
//                scope.launch {
                // Chiamiamo la funzione nel ViewModel
                viewModel.performLogin(
                    utente = utente,
                    pass = password,
                    onSuccess = {
                        onLoginSuccess()
                    }, onError = { errorMessage ->
                        // Ora il contesto è valido e il toast funzionerà!
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                )
//                }
            },
            modifier = Modifier.fillMaxWidth() ,
            enabled = !isLoading // Il bottone si disabilita mentre carica
        ) {
            if (isLoading) {
                // Mostra un piccolo cerchio che gira dentro il bottone
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Entra")
            }
        }
        Spacer(modifier = Modifier.height(50.dp))

        // Bottone Google (Immagine cliccabile)
        // Assicurati di avere un'icona "ic_google" nella cartella res/drawable
        Image(
            painter = painterResource(id = R.drawable.ic_google),
            contentDescription = "Login con Google",
            modifier = Modifier
                .size(40.dp)
                .clickable { onGoogleLogin() }
        )
    }
}