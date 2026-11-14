package app.walsoup.serifu.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.ui.nav.Routes
import app.walsoup.serifu.ui.nav.SerifuNavHost
import app.walsoup.serifu.ui.design.Serifu
import app.walsoup.serifu.ui.theme.SerifuTheme

class MainActivity : ComponentActivity() {
    private var pendingNavRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNavRoute = Routes.allowed(intent?.getStringExtra(EXTRA_NAV_ROUTE))
        val app = application as SerifuApp
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
            SerifuTheme {
                val loaded = settings
                if (loaded == null) {
                    Box(Modifier.fillMaxSize().background(Serifu.colors.bg))
                } else {
                    SerifuNavHost(
                        onboardingDone = loaded.onboardingDone,
                        pendingRoute = pendingNavRoute,
                        onConsumePendingRoute = { pendingNavRoute = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavRoute = Routes.allowed(intent.getStringExtra(EXTRA_NAV_ROUTE))
    }

    override fun onResume() {
        super.onResume()
        (application as? SerifuApp)?.checkAccessibilityService()
    }

    companion object {
        const val EXTRA_NAV_ROUTE = "serifu.navRoute"
    }
}
