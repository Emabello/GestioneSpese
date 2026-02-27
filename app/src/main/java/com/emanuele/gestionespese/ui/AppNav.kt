package com.emanuele.gestionespese.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.emanuele.gestionespese.ui.screens.DraftsScreen
import com.emanuele.gestionespese.ui.screens.HomeScreen
import com.emanuele.gestionespese.ui.screens.SpesaFormScreen
import com.emanuele.gestionespese.ui.screens.SummaryScreen
import com.emanuele.gestionespese.ui.viewmodel.SpeseViewModel

object Routes {
    const val HOME = "home"
    const val SUMMARY = "summary"
    const val FORM = "form"
    const val DRAFTS = "drafts"
}

@Composable
fun AppNav(vm: SpeseViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onAdd = {
                    vm.clearDraftPrefill()
                    nav.navigate(Routes.FORM)
                },
                onSummary = { nav.navigate(Routes.SUMMARY) },
                onEdit = { id -> nav.navigate("${Routes.FORM}?id=$id") },
                onDrafts = { nav.navigate(Routes.DRAFTS) }
            )
        }
        composable(Routes.SUMMARY) {
            SummaryScreen(vm = vm, onBack = { nav.popBackStack() })
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
        composable(Routes.DRAFTS) {

            val context = androidx.compose.ui.platform.LocalContext.current
            val db = com.emanuele.gestionespese.di.ServiceLocator.db(context)
            val repo = remember {
                com.emanuele.gestionespese.data.repo.SpesaDraftRepository(
                    db.spesaDraftDao()
                )
            }

            val vmDraft: com.emanuele.gestionespese.ui.drafts.DraftsViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = com.emanuele.gestionespese.ui.drafts.DraftsVmFactory(repo)
                )

            DraftsScreen(
                vm = vmDraft,
                onBack = { nav.popBackStack() },
                onOpenDraft = { draft ->
                    vm.prefillFromDraft(
                        importo = draft.amountCents / 100.0,
                        descrizione = draft.descrizione,
                        dataMillis = draft.dateMillis,
                        metodo = "Webank" // fisso
                    )

                    nav.navigate("${Routes.FORM}?id=-1")

                    // ❌ per ora NON cancelliamo il draft, così testi
                    vmDraft.delete(draft.id)
                }
            )
        }
    }
}