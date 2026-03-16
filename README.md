<div align="center">

# 💸 Gestione Spese

**La tua finanza personale, sempre con te.**

[![Build & APK](https://github.com/Emabello/GestioneSpese/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Emabello/GestioneSpese/actions/workflows/build-apk.yml)
[![CodeQL](https://github.com/Emabello/GestioneSpese/actions/workflows/codeql.yml/badge.svg)](https://github.com/Emabello/GestioneSpese/actions/workflows/codeql.yml)
![Android](https://img.shields.io/badge/Android-Kotlin-green?logo=android)
![Material3](https://img.shields.io/badge/UI-Material%203-teal?logo=materialdesign)
![License](https://img.shields.io/badge/license-Private-red)

</div>

---

## ✨ Cos'è

**Gestione Spese** è un'app Android nata per tenere traccia delle proprie spese e entrate in modo semplice, veloce e personale. Niente abbonamenti, niente cloud oscuri — i tuoi dati vivono su un Google Sheet che controlli tu.

> Ogni utente ha la propria dashboard personalizzabile. Ogni movimento è tuo.

---

## 🚀 Funzionalità principali

| Funzione | Descrizione |
|---|---|
| 📊 **Dashboard personalizzabile** | Scegli quali widget vedere: saldo, uscite, entrate, grafico torta, top categorie, ultimi movimenti |
| 📱 **Offline-first** | I dati vengono sincronizzati in locale — l'app funziona anche senza connessione |
| 🔐 **Login sicuro** | Accesso con utenza/password o con account Google collegato |
| 👆 **Biometria** | Sblocca l'app con impronta digitale o riconoscimento viso |
| 🔔 **Notifiche bancarie** | Cattura automaticamente le notifiche di Webank e le converte in bozze di spesa |
| 🎨 **Dark & Light mode** | Tema automatico basato sul sistema |
| ⚙️ **Gestione dati** | Configura tipologie, categorie, sottocategorie e conti direttamente dall'app |

---

## 🏗️ Architettura
```
app/
├── data/
│   ├── local/          # Room database (offline cache)
│   ├── remote/         # Retrofit + Google Apps Script API
│   ├── model/          # Data classes
│   └── repo/           # Repository pattern
├── ui/
│   ├── screens/        # Compose screens
│   ├── components/     # Widget dashboard
│   ├── viewmodel/      # ViewModel layer
│   └── theme/          # Material3 theme
└── utils/              # Helper functions
```

### Stack tecnologico

- **UI**: Jetpack Compose + Material 3
- **Architettura**: MVVM + Repository pattern
- **Database locale**: Room
- **API**: Retrofit → Google Apps Script → Google Sheets
- **Auth**: Credential Manager (Google Sign-In) + AndroidX Biometric
- **CI/CD**: GitHub Actions

---

## 📲 Backend

Il backend è un **Google Apps Script** collegato a un Google Sheet. Zero server, zero costi.
```
Android App
    ↕ Retrofit (HTTPS)
Google Apps Script (doGet / doPost)
    ↕
Google Sheets (database)
```

Le tabelle gestite:
- `UTENTE` — anagrafica utenti + Google ID
- `SPESE` — tutti i movimenti
- `TIPOLOGIA / CATEGORIA / SOTTOCATEGORIA / CONTO` — lookup
- `UC / UTCS` — associazioni utente ↔ conti e categorie
- `DASHBOARD` — layout widget personalizzato per utente
- `LOG` — log di tutte le chiamate API

---

## 🔄 CI/CD

Ogni push su `master` scatena automaticamente:

1. ✅ **Build** del APK debug
2. 🔍 **CodeQL** security analysis
3. 📦 **Release** automatica con APK allegato

Scarica sempre l'ultima build dalla sezione [Releases](../../releases).

---

## 🛠️ Setup sviluppo

### Prerequisiti
- Android Studio Hedgehog o superiore
- JDK 17
- Account Google Cloud con OAuth configurato

### Configurazione

1. Clona il repository:
```bash
git clone https://github.com/Emabello/GestioneSpese.git
```

2. Crea il file `app/google-services.json` dal tuo progetto Google Cloud

3. Configura le variabili in `res/values/strings.xml`:
```xml
<string name="backend_url">https://script.google.com/macros/s/TUO_ID/exec</string>
<string name="backend_api_key">TUO_API_KEY</string>
```

4. Aggiungi i secrets su GitHub per la CI:
   - `GOOGLE_SERVICES_JSON` → contenuto del `google-services.json`

5. Build & run! 🚀

---

## 📁 Branch strategy
```
master          ← sempre stabile, deploy automatico
  └── feature/nome-feature   ← sviluppo
  └── fix/nome-bug           ← bug fix
  └── hotfix/urgente         ← hotfix critici
```

---

## 👤 Autore

**Emanuele Bellotti** — sviluppatore Android & SAP ABAP

---

<div align="center">
  <sub>Fatto con ❤️ e troppo caffè ☕</sub>
</div>
