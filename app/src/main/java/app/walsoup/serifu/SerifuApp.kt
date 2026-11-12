package app.walsoup.serifu

import android.app.Application
import app.walsoup.serifu.debug.PipelineTimings
import app.walsoup.serifu.ml.BergamotModelManager
import app.walsoup.serifu.service.EventRouter
import app.walsoup.serifu.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import app.walsoup.serifu.service.SerifuAccessibilityService

class SerifuApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var bergamotModels: BergamotModelManager
        private set
    val readerPresence = MutableStateFlow(EventRouter.State())
    val pipelineTimings = MutableStateFlow(PipelineTimings())
    val isServiceActive = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this, appScope)
        bergamotModels = BergamotModelManager(this)
        checkAccessibilityService()
    }

    fun checkAccessibilityService(): Boolean {
        if (SerifuAccessibilityService.isRunning) {
            isServiceActive.value = true
            return true
        }
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabled = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
        val active = enabled.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                (it.resolveInfo.serviceInfo.name == "app.walsoup.serifu.service.SerifuAccessibilityService" ||
                    it.resolveInfo.serviceInfo.name.endsWith(".SerifuAccessibilityService"))
        }
        isServiceActive.value = active
        return active
    }

    companion object {
        const val SERVICE_CLASS = "app.walsoup.serifu.service.SerifuAccessibilityService"

        fun openAccessibilitySettings(context: Context) {
            val component = ComponentName(context.packageName, SERVICE_CLASS).flattenToString()
            val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, component)
                putExtra("extra_fragment_arg_key", component)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(details)
            } catch (_: Throwable) {
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Throwable) {}
            }
        }
    }
}

