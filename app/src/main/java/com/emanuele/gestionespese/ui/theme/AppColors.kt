/**
 * AppColors.kt
 *
 * Extension properties su [MaterialTheme] che restituiscono i colori semantici
 * corretti in base alla modalità chiara/scura del sistema.
 *
 * Uso: `MaterialTheme.incomeContainer` invece di `IncomeContainer`
 * così il colore si adatta automaticamente al tema attivo.
 */
package com.emanuele.gestionespese.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Sfondo container per valori positivi/entrate (verde chiaro in light, verde scuro in dark). */
val MaterialTheme.incomeContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) D_IncomeContainer else IncomeContainer

/** Sfondo container per valori negativi/uscite (grigio chiaro in light, blu scuro in dark). */
val MaterialTheme.expenseContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) D_ExpenseContainer else ExpenseContainer

/** Testo su incomeContainer. */
val MaterialTheme.onIncome: Color
    @Composable get() = if (isSystemInDarkTheme()) D_OnIncome else OnIncome

/** Testo su expenseContainer. */
val MaterialTheme.onExpense: Color
    @Composable get() = if (isSystemInDarkTheme()) D_OnExpense else OnExpense
