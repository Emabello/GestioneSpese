package com.emanuele.gestionespese.data.local

import com.emanuele.gestionespese.data.local.entities.BankProfileEntity
import com.emanuele.gestionespese.data.local.entities.ParseRuleEntity

/**
 * Seed idempotente del profilo Webank.
 *
 * Inserisce il profilo Webank con le sue regole di parsing nel database se non è
 * già presente. Le regex sono estratte dal parser originale [WebankParser.kt].
 *
 * Va chiamato una sola volta in [MyApp.onCreate] su Dispatchers.IO, wrappato in try/catch.
 */
object WebankSeed {

    private const val WEBANK_PKG = "com.opentecheng.android.webank"

    suspend fun seedIfNeeded(dao: BankProfileDao) {
        val alreadyExists = dao.getActiveProfiles().any { it.packageName == WEBANK_PKG }
        if (alreadyExists) return

        val profileId = dao.insertProfile(
            BankProfileEntity(
                displayName   = "Webank",
                packageName   = WEBANK_PKG,
                isActive      = true,
                contentSource = "TEXT_OR_BIG"
            )
        )
        if (profileId <= 0) return // insert ignorato (UNIQUE conflict)

        // ── Regola AMOUNT (Pattern 1: ADDEBITO GENERICO CARTA) ────────────────
        // Cattura 2 gruppi: (euro)(centesimi)
        // Es: "...di -2,50 EURO..." → groups[1]=2, groups[2]=50
        dao.upsertRule(ParseRuleEntity(
            bankProfileId = profileId,
            field         = "AMOUNT",
            regex         = """ADDEBITO GENERICO CARTA\*\d+\s+\S+\s+DIGIT-\d+:\d+-.+?\s+di\s+-?(\d+)[,.](\d{2})\s*EURO""",
            groupIndex    = 1,
            priority      = 0,
            description   = "Importo da ADDEBITO GENERICO CARTA"
        ))

        // ── Regola AMOUNT (Pattern 2: Pagamento autorizzato) ──────────────────
        // Cattura 2 gruppi: (euro)(centesimi)
        dao.upsertRule(ParseRuleEntity(
            bankProfileId = profileId,
            field         = "AMOUNT",
            regex         = """(?:€\s*)?(\d+)[,.](\d{2})\s*(?:Euro|EUR|€)?""",
            groupIndex    = 1,
            priority      = 1,
            description   = "Importo da Pagamento autorizzato (fallback)"
        ))

        // ── Regola MERCHANT (Pattern 1: ADDEBITO GENERICO CARTA) ─────────────
        // Cattura 1 gruppo: nome merchant
        // Es: "...DIGIT-00:00-MILAN HOLIDAY S.A di..." → group[1]="MILAN HOLIDAY S.A"
        dao.upsertRule(ParseRuleEntity(
            bankProfileId = profileId,
            field         = "MERCHANT",
            regex         = """ADDEBITO GENERICO CARTA\*\d+\s+\S+\s+DIGIT-\d+:\d+-(.+?)\s+di\s+-?""",
            groupIndex    = 1,
            priority      = 0,
            description   = "Merchant da ADDEBITO GENERICO CARTA"
        ))

        // ── Regola MERCHANT (Pattern 2: "Euro – NOME con Carta") ─────────────
        dao.upsertRule(ParseRuleEntity(
            bankProfileId = profileId,
            field         = "MERCHANT",
            regex         = """Euro\s*[-–]\s*(.*?)\s*con\s*Carta""",
            groupIndex    = 1,
            priority      = 1,
            description   = "Merchant da 'Euro – NOME con Carta'"
        ))

        // ── Regola DATE (formato esplicito: Data: dd/MM/yyyy Ora: HH:mm) ─────
        // Cattura 5 gruppi: (dd)(MM)(yyyy)(HH)(mm)
        dao.upsertRule(ParseRuleEntity(
            bankProfileId = profileId,
            field         = "DATE",
            regex         = """Data:\s*(\d{2})/(\d{2})/(\d{4})\s+Ora:\s*(\d{2}):(\d{2})""",
            groupIndex    = 1,
            priority      = 0,
            description   = "Data e ora esplicite (Data: dd/MM/yyyy Ora: HH:mm)"
        ))
    }
}
