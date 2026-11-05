package app.walsoup.serifu.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.ui.nav.SerifuNavHost
import app.walsoup.serifu.ui.design.Serifu
import app.walsoup.serifu.ui.theme.SerifuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SerifuApp
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
            SerifuTheme {
                val loaded = settings
                if (loaded == null) {
                    Box(Modifier.fillMaxSize().background(Serifu.colors.bg))
                } else {
                    SerifuNavHost(onboardingDone = loaded.onboardingDone)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as? SerifuApp)?.checkAccessibilityService()
    }
}
