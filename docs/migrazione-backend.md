# Migrazione backend: da Supabase a Google Apps Script

## Dove cambiare il reperimento dati nell'app

I punti chiave sono:

1. `app/src/main/res/values/strings.xml`
   - `backend_url`: endpoint base del Web App Apps Script.
   - `backend_api_key`: chiave usata come query param `?key=...`.

2. `app/src/main/java/com/emanuele/gestionespese/data/remote/SupabaseApi.kt`
   - Tutti i metodi Retrofit ora puntano a `exec` con query `resource=...`.

3. `app/src/main/java/com/emanuele/gestionespese/data/remote/SupabaseAuthInterceptor.kt`
   - Aggiunge automaticamente `key=<api_key>` a ogni chiamata.

4. `app/src/main/java/com/emanuele/gestionespese/data/repo/SpeseRepository.kt`
   - Mapping tra modello app e payload Apps Script.
   - Alias tra vecchi nomi Supabase e nuove colonne foglio.

5. `docs/google-apps-script.gs`
   - Script completo da incollare in Apps Script.
   - Espone metodi GET/POST per lista, dettaglio, categorie, sottocategorie, insert, update, delete.

## Flusso Git consigliato per la nuova versione

```bash
git checkout -b feat/migrazione-apps-script
# fai modifiche
git add .
git commit -m "feat: migra backend da supabase a google apps script"
git push -u origin feat/migrazione-apps-script
```

Poi apri PR verso il branch principale (`main` o `master`).

## Strategia alias colonne

Per ridurre impatti sul codice UI:

- In lettura spese, lo script restituisce sia campi “nuovi” che alias compatibili (`categoria_link_id`, `categoria_id`, `metodo_pagamento`, ecc.).
- Nell'app, `SpesaView` usa `@SerializedName(..., alternate = [...])` così accetta più nomi senza rompere schermate esistenti.

## Deploy Apps Script

1. Apri progetto Apps Script.
2. Incolla `docs/google-apps-script.gs` nel file `.gs` principale.
3. **Deploy > Manage deployments > Edit > New version > Deploy**.
4. Verifica endpoint `/exec` e usa la stessa `API_KEY` anche nell'app Android.
