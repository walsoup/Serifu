package app.walsoup.serifu.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.serifu.core.Tuning
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.ui.home.HomeScreen
import app.walsoup.serifu.ui.home.HomeViewModel
import app.walsoup.serifu.ui.onboarding.OnboardingScreen
import app.walsoup.serifu.ui.settings.AboutScreen
import app.walsoup.serifu.ui.settings.BehaviourScreen
import app.walsoup.serifu.ui.settings.DebugScreen
import app.walsoup.serifu.ui.settings.EngineScreen
import app.walsoup.serifu.ui.settings.LanguagesScreen
import app.walsoup.serifu.ui.settings.LetteringScreen
import app.walsoup.serifu.ui.settings.ReadersScreen

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

    fun start(onboardingDone: Boolean): String = if (onboardingDone) Home else onboarding(1)

    fun allows(route: String): Boolean {
        if (route in ALLOWED) return true
        if (!route.startsWith("onboarding/")) return false
        val step = route.removePrefix("onboarding/").toIntOrNull() ?: return false
        return step in 1..Tuning.ONBOARDING_STEPS && route == onboarding(step)
    }

    fun allowed(route: String?): String? = route?.takeIf { allows(it) }

    private val ALLOWED = setOf(Home, Languages, Readers, Lettering, Behaviour, Engine, Debug, About)
}

@Composable
fun SerifuNavHost(
    onboardingDone: Boolean,
    pendingRoute: String? = null,
    onConsumePendingRoute: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as SerifuApp
    val activity = context as ComponentActivity
    val viewModel: HomeViewModel = viewModel(viewModelStoreOwner = activity) {
        HomeViewModel(app.settingsRepository)
    }
    val nav = rememberNavController()
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute
        if (route.isNullOrBlank()) return@LaunchedEffect
        onConsumePendingRoute()
        if (!Routes.allows(route)) return@LaunchedEffect
        val known = try {
            if (route.startsWith("onboarding/")) {
                nav.graph.findNode(Routes.Onboarding) != null
            } else {
                nav.graph.findNode(route) != null
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("SerifuNav", "Rejected navigation route", e)
            false
        }
        if (!known) return@LaunchedEffect
        try {
            nav.navigate(route) {
                launchSingleTop = true
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("SerifuNav", "Rejected navigation route", e)
        }
    }
    NavHost(
        navController = nav,
        startDestination = Routes.start(onboardingDone),
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(200))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(200))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(180))
        },
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
                onBack = { nav.popBackStack() },
                onHome = {
                    nav.navigate(Routes.Home) {
                        popUpTo(nav.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Languages) {
            LanguagesScreen(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onOpenEngine = { nav.navigate(Routes.Engine) },
            )
        }
        composable(Routes.Readers) { ReadersScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.Lettering) { LetteringScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.Behaviour) { BehaviourScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.Engine) {
            EngineScreen(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onOpenLanguages = { nav.navigate(Routes.Languages) },
            )
        }
        composable(Routes.Debug) { DebugScreen(viewModel) { nav.popBackStack() } }
        composable(Routes.About) { AboutScreen { nav.popBackStack() } }
    }
}
