package com.emanuele.gestionespese.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.emanuele.gestionespese.MyApp
import com.emanuele.gestionespese.data.repo.DashboardRepository
import com.emanuele.gestionespese.data.repo.SpesaDraftRepository
import com.emanuele.gestionespese.ui.drafts.DraftsViewModel
import com.emanuele.gestionespese.ui.screens.ConfigScreen
import com.emanuele.gestionespese.ui.screens.DashboardEditScreen
import com.emanuele.gestionespese.ui.screens.DraftsScreen
import com.emanuele.gestionespese.ui.screens.HomeScreen
import com.emanuele.gestionespese.ui.screens.SettingsScreen
import com.emanuele.gestionespese.ui.screens.SpesaFormScreen
import com.emanuele.gestionespese.ui.screens.SummaryScreen
import com.emanuele.gestionespese.ui.screens.SyncLoadingScreen
import com.emanuele.gestionespese.ui.theme.Brand
import com.emanuele.gestionespese.ui.viewmodel.DashboardViewModel
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel

object Routes {
    const val MAIN           = "main"
    const val FORM           = "form"
    const val DASHBOARD_EDIT = "dashboard_edit"
    const val CONFIG         = "config"
}

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

    if (!state.syncDone) {
        SyncLoadingScreen(error = state.error, onRetry = { vm.syncAll() })
        return
    }

    val context = LocalContext.current
    val dashVm: DashboardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app  = context.applicationContext as MyApp
                val repo = DashboardRepository(
                    dao = app.db.dashboardDao(),
                    api = app.api
                )
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(
                    repo   = repo,
                    utente = app.currentUserLabel ?: ""
                ) as T
            }
        }
    )

    NavHost(navController = nav, startDestination = Routes.MAIN) {

        composable(Routes.MAIN) {
            MainTabScreen(
                vm                 = vm,
                dashVm             = dashVm,
                onNavigateToForm   = { editId, draftId ->
                    if (editId == null) vm.clearDraftPrefill()
                    val route = when {
                        editId != null && draftId != null -> "${Routes.FORM}?id=$editId&draftId=$draftId"
                        editId != null                    -> "${Routes.FORM}?id=$editId"
                        draftId != null                   -> "${Routes.FORM}?id=-1&draftId=$draftId"  // ← id=-1 esplicito
                        else                              -> Routes.FORM
                    }
                    nav.navigate(route)
                },
                onEditDashboard    = { nav.navigate(Routes.DASHBOARD_EDIT) },
                onNavigateToConfig = { nav.navigate(Routes.CONFIG) }
            )
        }

        composable(
            route = "${Routes.FORM}?id={id}&draftId={draftId}",
            arguments = listOf(
                navArgument("id") {
                    type         = NavType.IntType
                    defaultValue = -1
                },
                navArgument("draftId") {
                    type         = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val id      = backStackEntry.arguments?.getInt("id")   ?: -1
            val draftId = backStackEntry.arguments?.getLong("draftId") ?: -1L

            SpesaFormScreen(
                vm      = vm,
                editingId = id,
                draftId = if (draftId == -1L) null else draftId,
                onBack  = { nav.popBackStack() }
            )
        }

        composable(Routes.DASHBOARD_EDIT) {
            DashboardEditScreen(dashVm = dashVm, onBack = { nav.popBackStack() })
        }

        composable(Routes.CONFIG) {
            ConfigScreen(onBack = { nav.popBackStack() })
        }
    }
}

@Composable
fun MainTabScreen(
    vm: SpeseViewModel,
    dashVm: DashboardViewModel,
    onNavigateToForm: (editId: Int?, draftId: Long?) -> Unit,  // ← aggiornata
    onEditDashboard: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val context = LocalContext.current
    val db      = remember { (context.applicationContext as MyApp).db }
    val repo    = remember { SpesaDraftRepository(db.spesaDraftDao()) }
    val vmDraft: DraftsViewModel = viewModel(factory = DraftsViewModel.factory(repo))

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        icon     = { Icon(tab.icon, contentDescription = tab.label) },
                        label    = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == MainTab.HOME.ordinal) {
                FloatingActionButton(
                    onClick        = { onNavigateToForm(null, null) },
                    containerColor = Brand
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                MainTab.SUMMARY.ordinal  -> SummaryScreen(
                    vm              = vm,
                    dashVm          = dashVm,
                    onBack          = { },
                    onEditDashboard = onEditDashboard
                )
                MainTab.HOME.ordinal     -> HomeScreen(
                    vm        = vm,
                    onAdd     = { onNavigateToForm(null, null) },
                    onEdit    = { id -> onNavigateToForm(id, null) },
                    onSummary = { selectedTab = MainTab.SUMMARY.ordinal },
                    onDrafts  = { selectedTab = MainTab.DRAFTS.ordinal }
                )
                MainTab.DRAFTS.ordinal   -> DraftsScreen(
                    vm          = vmDraft,
                    onBack      = { selectedTab = MainTab.HOME.ordinal },
                    onOpenDraft = { draft ->
                        android.util.Log.d("DRAFT_DEBUG", "=== APRO DRAFT ===")
                        android.util.Log.d("DRAFT_DEBUG", "amountCents=${draft.amountCents}")
                        android.util.Log.d("DRAFT_DEBUG", "importo=${draft.amountCents / 100.0}")
                        android.util.Log.d("DRAFT_DEBUG", "descrizione=${draft.descrizione}")
                        android.util.Log.d("DRAFT_DEBUG", "dateMillis=${draft.dateMillis}")
                        android.util.Log.d("DRAFT_DEBUG", "metodo=${draft.metodoPagamento}")

                        onNavigateToForm(null, draft.id)

                        vm.prefillFromDraft(
                            importo     = draft.amountCents / 100.0,
                            descrizione = draft.descrizione,
                            dataMillis  = draft.dateMillis,
                            metodo      = draft.metodoPagamento
                        )
                        // ← NON cancella il draft qui — lo cancellerà SpesaFormScreen dopo il salvataggio
                    }
                )
                MainTab.SETTINGS.ordinal -> SettingsScreen(
                    vm                 = vm,
                    onNavigateToConfig = onNavigateToConfig
                )
            }
        }
    }
}