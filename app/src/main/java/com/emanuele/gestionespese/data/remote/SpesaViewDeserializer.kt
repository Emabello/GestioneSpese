/**
 * SpesaViewDeserializer.kt
 *
 * Custom Gson deserializer per [SpesaView] che gestisce gracefully i mismatch
 * di tipo nei campi della risposta GAS. Necessario perché record storici nel
 * foglio GAS possono avere colonne in ordine diverso, producendo ad esempio:
 *   - `importo: ""` (stringa vuota dove è atteso Double)
 *   - `tipo: 12.5`  (numero dove è attesa String)
 *
 * Senza questo deserializer, Gson lancerebbe JsonSyntaxException e l'intero
 * payload `sync_all` verrebbe scartato, forzando il fallback alle 6 chiamate
 * individuali invece della singola chiamata batch.
 */
package com.emanuele.gestionespese.data.remote

import com.emanuele.gestionespese.data.model.SpesaView
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class SpesaViewDeserializer : JsonDeserializer<SpesaView> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): SpesaView {
        val obj = json.asJsonObject
        return SpesaView(
            id                 = obj["id"]?.safeInt() ?: 0,
            utente             = obj["utente"]?.safeString(),
            data               = obj["data"]?.safeString(),
            conto              = obj["conto"]?.safeString(),
            conto_destinazione = obj["conto_destinazione"]?.safeString(),
            importo            = obj["importo"]?.safeDouble() ?: 0.0,
            tipo               = obj["tipo"]?.safeString(),
            tipo_movimento     = obj["tipo_movimento"]?.safeString(),
            categoria          = obj["categoria"]?.safeString(),
            sottocategoria     = obj["sottocategoria"]?.safeString(),
            descrizione        = obj["descrizione"]?.safeString(),
            mese               = obj["mese"]?.safeInt(),
            anno               = obj["anno"]?.safeInt()
        )
    }

    private fun JsonElement.safeDouble(): Double? = try {
        asDouble
    } catch (_: Exception) {
        null
    }

    private fun JsonElement.safeInt(): Int? = try {
        asInt
    } catch (_: Exception) {
        null
    }

    private fun JsonElement.safeString(): String? = try {
        if (isJsonNull) null else asString
    } catch (_: Exception) {
        null
    }
}
