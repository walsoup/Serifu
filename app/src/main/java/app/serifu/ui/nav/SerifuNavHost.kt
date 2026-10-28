package app.serifu.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.serifu.SerifuApp
import app.serifu.ui.home.HomeScreen
import app.serifu.ui.home.HomeViewModel
import app.serifu.ui.onboarding.OnboardingScreen
import app.serifu.ui.settings.AboutScreen
import app.serifu.ui.settings.BehaviourScreen
import app.serifu.ui.settings.DebugScreen
import app.serifu.ui.settings.EngineScreen
import app.serifu.ui.settings.LanguagesScreen
import app.serifu.ui.settings.LetteringScreen
import app.serifu.ui.settings.ReadersScreen

object Routes {
    const val Home = "home"
    const val Onboarding = "onboarding/{step}"
    const val Languages = "settings/languages"
    const val Readers = "settings/readers"
    const val Lettering = "settings/lettering"
    const val Behaviour = "settings/behaviour"
    const val Engine = "settings/engine"
    const val Debug = "settings/debug"
    const val About = "about"

    fun onboarding(step: Int) = "onboarding/$step"
}

@Composable
fun SerifuNavHost(startAtOnboarding: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as SerifuApp
    val activity = context as ComponentActivity
    val viewModel: HomeViewModel = viewModel(viewModelStoreOwner = activity) {
        HomeViewModel(app.settingsRepository)
    }
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (startAtOnboarding) Routes.onboarding(1) else Routes.Home,
    ) {
        composable(Routes.Home) {
            HomeScreen(viewModel) { route -> nav.navigate(route) }
        }
        composable(
            route = Routes.Onboarding,
            arguments = listOf(navArgument("step") { type = NavType.IntType }),
        ) { entry ->
            OnboardingScreen(
                step = entry.arguments?.getInt("step") ?: 1,
                viewModel = viewModel,
                onStep = { step -> nav.navigate(Routes.onboarding(step)) },
                onHome = {
                    nav.navigate(Routes.Home) {
                        popUpTo(nav.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Languages) { LanguagesScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.Readers) { ReadersScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.Lettering) { LetteringScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.Behaviour) { BehaviourScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.Engine) { EngineScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.Debug) { DebugScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.About) { AboutScreen { nav.popBackStack() } }
    }
}
