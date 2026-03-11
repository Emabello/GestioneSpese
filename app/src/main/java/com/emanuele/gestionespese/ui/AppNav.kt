package com.emanuele.gestionespese.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.repo.SpesaDraftRepository
import com.emanuele.gestionespese.ui.drafts.DraftsVmFactory
import com.emanuele.gestionespese.ui.drafts.DraftsViewModel
import com.emanuele.gestionespese.ui.screens.DraftsScreen
import com.emanuele.gestionespese.ui.screens.HomeScreen
import com.emanuele.gestionespese.ui.screens.SettingsScreen
import com.emanuele.gestionespese.ui.screens.SpesaFormScreen
import com.emanuele.gestionespese.ui.screens.SummaryScreen
import com.emanuele.gestionespese.ui.screens.SyncLoadingScreen
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel

object Routes {
    const val MAIN = "main"
    const val FORM = "form"
}

// Tab della bottom bar
enum class MainTab(
    val label: String,
    val icon: ImageVector
) {
    SUMMARY("Riepilogo", Icons.Default.Home),
    HOME("Movimenti", Icons.AutoMirrored.Filled.List),
    DRAFTS("Notifiche", Icons.Default.Notifications),
    SETTINGS("Impostazioni", Icons.Default.Settings)
}

@Composable
fun AppNav(vm: SpeseViewModel) {
    val nav   = rememberNavController()
    val state by vm.state.collectAsState()

    // Finché il sync non è completato mostra la schermata di caricamento
    if (!state.syncDone) {
        SyncLoadingScreen(error = state.error, onRetry = { vm.syncAll() })
        return
    }

    NavHost(navController = nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainTabScreen(
                vm = vm,
                onNavigateToForm = { id ->
                    if (id == null) vm.clearDraftPrefill()
                    nav.navigate(if (id != null) "${Routes.FORM}?id=$id" else Routes.FORM)
                }
            )
        }
        composable(
            route = "${Routes.FORM}?id={id}",
            arguments = listOf(navArgument("id") {
                type = NavType.IntType
                defaultValue = -1
            })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: -1
            SpesaFormScreen(vm = vm, editingId = id, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
fun MainTabScreen(
    vm: SpeseViewModel,
    onNavigateToForm: (id: Int?) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val context = LocalContext.current
    val db = remember { (context.applicationContext as MyApp).db }
    val repo = remember { SpesaDraftRepository(db.spesaDraftDao()) }
    val vmDraft: DraftsViewModel = viewModel(factory = DraftsVmFactory(repo))

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            MainTab.SUMMARY.ordinal -> SummaryScreen(
                vm = vm,
                onBack = { /* non serve, siamo in tab */ }
            )

            MainTab.HOME.ordinal -> HomeScreen(
                vm = vm,
                onAdd = { onNavigateToForm(null) },
                onEdit = { id -> onNavigateToForm(id) },
                // questi callback non servono più — navigazione ora è nei tab
                onSummary = { selectedTab = MainTab.SUMMARY.ordinal },
                onDrafts = { selectedTab = MainTab.DRAFTS.ordinal }
            )

            MainTab.DRAFTS.ordinal -> DraftsScreen(
                vm = vmDraft,
                onBack = { selectedTab = MainTab.HOME.ordinal },
                onOpenDraft = { draft ->
                    vm.prefillFromDraft(
                        importo = draft.amountCents / 100.0,
                        descrizione = draft.descrizione,
                        dataMillis = draft.dateMillis,
                        metodo = draft.metodoPagamento
                    )
                    vmDraft.delete(draft.id)
                    onNavigateToForm(null)
                }
            )

            MainTab.SETTINGS.ordinal -> SettingsScreen()
        }
    }
}