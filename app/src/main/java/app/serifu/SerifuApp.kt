package app.serifu

import android.app.Application
import app.serifu.debug.PipelineTimings
import app.serifu.service.EventRouter
import app.serifu.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class SerifuApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settingsRepository: SettingsRepository
        private set
    val readerPresence = MutableStateFlow(EventRouter.State())
    val pipelineTimings = MutableStateFlow(PipelineTimings())

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this, appScope)
    }
}
