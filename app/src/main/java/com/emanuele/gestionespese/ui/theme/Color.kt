/**
 * Color.kt
 *
 * Palette colori dell'app. Definisce i colori brand (teal elegante),
 * i colori semantici (entrate in verde, uscite in rosso) e i neutri
 * usati nei temi chiaro e scuro.
 */
package com.emanuele.gestionespese.ui.theme

import androidx.compose.ui.graphics.Color

// Brand / Accent (teal elegante)
val Brand = Color(0xFF2AA39A)
val BrandDark = Color(0xFF1E7F78)

// Neutrali (sfondi e testi)
val Ink = Color(0xFF0F172A)          // testo principale
val InkSoft = Color(0xFF334155)      // testo secondario
val Muted = Color(0xFF64748B)        // label/placeholder

val Surface0 = Color(0xFFF7F8FA)     // background chiaro
val Surface1 = Color(0xFFFFFFFF)     // card
val Surface2 = Color(0xFFF1F5F9)     // container soft
val OutlineSoft = Color(0xFFE2E8F0)

// Stati / semantic
val Danger = Color(0xFFEF4444)

// Entrata/Uscita (container soft)
val IncomeContainer = Color(0xFFEAFBF5)
val ExpenseContainer = Color(0xFFF3F5F8)

val OnIncome = Color(0xFF0B3B2F)
val OnExpense = Color(0xFF1F2937)


// ===== Dark =====
val D_Ink = Color(0xFFE5E7EB)
val D_InkSoft = Color(0xFFCBD5E1)
val D_Muted = Color(0xFF3F83E8)

val D_Surface0 = Color(0xFF0B1220)
val D_Surface1 = Color(0xFF0F172A)
val D_Surface2 = Color(0xFF111C33)
val D_OutlineSoft = Color(0xFF23314D)

val D_IncomeContainer = Color(0xFF0E2A25)
val D_ExpenseContainer = Color(0xFF141B2D)

val D_OnIncome = Color(0xFFB7F3DE)
val D_OnExpense = Color(0xFFE2E8F0)