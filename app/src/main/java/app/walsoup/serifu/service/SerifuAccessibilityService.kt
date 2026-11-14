package app.walsoup.serifu.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import app.walsoup.serifu.R
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.capture.FrameConverter
import app.walsoup.serifu.capture.ScreenCapturer
import app.walsoup.serifu.coordinator.PagePipeline
import app.walsoup.serifu.coordinator.TranslationCoordinator
import app.serifu.core.coordinator.Event
import app.serifu.core.model.ModelVariant
import app.serifu.core.model.OnDeviceEngine
import app.serifu.core.model.QualityMode
import app.walsoup.serifu.ml.BergamotModelManager
import app.walsoup.serifu.ml.BergamotTranslationEngine
import app.walsoup.serifu.ml.MlKitTranslationEngine
import app.walsoup.serifu.ml.NetworkStatus
import app.walsoup.serifu.ml.OcrEngine
import app.walsoup.serifu.ml.OnDeviceTranslationEngine
import app.walsoup.serifu.ml.RemoteAiTranslationEngine
import app.walsoup.serifu.overlay.LetteringRenderer
import app.walsoup.serifu.overlay.OverlayManager
import app.walsoup.serifu.settings.Settings as SerifuSettings
import app.walsoup.serifu.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class SerifuAccessibilityService : AccessibilityService() {
    private val router = EventRouter()
    private lateinit var overlays: OverlayManager
    private var sessionJob = SupervisorJob()
    private var scope = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
    private var ocr = OcrEngine()
    private lateinit var translator: OnDeviceTranslationEngine
    private lateinit var capturer: ScreenCapturer
    private var loop: TranslationCoordinator? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var started = false
    val coordinator: TranslationCoordinator? get() = loop

    override fun onCreate() {
        super.onCreate()
        overlays = OverlayManager(this)
        capturer = ScreenCapturer(this, overlays, FrameConverter())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this
        val app = application as SerifuApp
        app.isServiceActive.value = true
        if (started) return
        if (!sessionJob.isActive) {
            sessionJob = SupervisorJob()
            scope = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
            ocr = OcrEngine()
            capturer = ScreenCapturer(this, overlays, FrameConverter())
        }
        val bergamotModels = app.bergamotModels
        val mlKit = MlKitTranslationEngine(
            // Null means the store has not emitted yet. The default is to allow
            // mobile data; only an explicit opt-in should wait for Wi-Fi.
            wifiOnly = { app.settingsRepository.settings.value?.wifiOnlyDownloads == true },
            isOnUnmeteredNetwork = { NetworkStatus.isUnmetered(applicationContext) },
        )
        val bergamot = BergamotTranslationEngine(
            models = bergamotModels,
            variant = { app.settingsRepository.settings.value?.bergamotVariant ?: ModelVariant.LITE },
        )
        translator = OnDeviceTranslationEngine(
            mlKit = mlKit,
            bergamot = bergamot,
            models = bergamotModels,
            preferred = {
                app.settingsRepository.settings.value?.onDeviceEngine
                    ?: OnDeviceEngine.MLKIT
            },
            variant = {
                app.settingsRepository.settings.value?.bergamotVariant
                    ?: ModelVariant.LITE
            },
            quality = {
                app.settingsRepository.settings.value?.quality
                    ?: QualityMode.FAST
            },
        )
        val remoteAi = RemoteAiTranslationEngine(
            settingsProvider = { app.settingsRepository.settings.value ?: SerifuSettings() },
        )
        val pagePipeline = PagePipeline(this, translator, LetteringRenderer(this), ocr, remoteAi = remoteAi)
        val reading = TranslationCoordinator(
            scope = scope,
            context = this,
            overlays = overlays,
            capturer = capturer,
            ocr = ocr,
            pipeline = pagePipeline,
            presence = { app.readerPresence.value },
            writeSettings = { transform ->
                scope.launch { app.settingsRepository.update(transform) }
            },
            clearObscuredFlag = {
                val current = app.readerPresence.value
                if (current.obscured) app.readerPresence.value = current.copy(obscured = false)
            },
            openApp = {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            openAppRoute = { route ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_NAV_ROUTE, route)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP,
                        ),
                )
            },
            notifyPermissionLost = ::notifyPermissionLost,
            publishTimings = { app.pipelineTimings.value = it },
            resolveEngineId = { settings ->
                translator.resolveEngineId(settings.sourceLanguage, settings.targetLanguage)
            },
        )
        reading.start()
        loop = reading
        overlays.events = reading
        overlays.attach()
        scope.launch {
            app.settingsRepository.settings.filterNotNull().collect { settings ->
                reading.pushSettings(settings)
                val isAiOnly = settings.quality == QualityMode.BEST_ONLY &&
                    pagePipeline.hasAiConfigured(settings)
                if (!isAiOnly) {
                    runCatching {
                        translator.prewarm(settings.sourceLanguage, settings.targetLanguage)
                    }
                }
            }
        }
        scope.launch {
            app.readerPresence.collect { presence ->
                reading.send(Event.ForegroundChanged(isReader = presence.reader))
            }
        }
        val powerManager = getSystemService(android.os.PowerManager::class.java)
        if (powerManager?.isInteractive == true) {
            reading.onScreenOn()
        }
        val receiver = ScreenStateReceiver(
            onScreenOff = {
                reading.hideForGesture()
                reading.onScreenOff()
            },
            onUserPresent = { reading.onScreenOn() },
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        screenReceiver = receiver
        started = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val incoming = event ?: return
        val app = application as? SerifuApp ?: return
        val reading = loop ?: return
        val readers = app.settingsRepository.settings.value?.readerPackages.orEmpty()
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
        val before = app.readerPresence.value
        val after = router.reduce(
            state = before,
            eventType = incoming.eventType,
            packageName = incoming.packageName?.toString(),
            className = incoming.className?.toString(),
            readers = readers,
            ownPackage = packageName,
            imePackage = ime,
        )
        app.readerPresence.value = after
        val className = incoming.className?.toString().orEmpty()
        val pkgName = incoming.packageName?.toString()
        val isCurrentReader = after.reader && pkgName != null && pkgName == after.foregroundPackage
        val fromReader = isCurrentReader || (pkgName != null && pkgName in readers)
        val ownOverlay = pkgName == packageName &&
            !className.endsWith("MainActivity") &&
            !className.endsWith("TestPageActivity") &&
            !isCurrentReader
        when (incoming.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (fromReader) {
                reading.hideForGesture()
                reading.send(Event.Scrolled)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> if (!ownOverlay) {
                val powerManager = getSystemService(android.os.PowerManager::class.java)
                if (powerManager?.isInteractive == true && !reading.screenIsOn) {
                    reading.onScreenOn()
                }
                reading.hideForGesture()
                when {
                    !after.reader -> reading.send(Event.ForegroundChanged(isReader = false))
                    after.obscured -> reading.send(Event.ObscuredChanged(obscured = true))
                    !before.reader || !reading.readerForeground -> reading.send(Event.ForegroundChanged(isReader = true))
                    before.obscured -> reading.send(Event.ObscuredChanged(obscured = false))
                    else -> reading.send(Event.WindowChanged)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (fromReader) reading.send(Event.ContentChanged)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val reading = loop ?: return false
        if (!reading.readerForeground) return false
        reading.hideForGesture()
        reading.send(Event.VolumeKey)
        return false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loop?.hideForGesture()
        loop?.onConfigurationChanged()
    }

    override fun onInterrupt() = Unit

    private fun notifyPermissionLost() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_serifu)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.tooltip_permission))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(PERMISSION_NOTE, notification)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            loop?.send(app.serifu.core.coordinator.Event.ConfigurationChanged)
        }
        @Suppress("DEPRECATION")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            ocr.close()
            ocr = OcrEngine()
            if (::translator.isInitialized) {
                translator.trimMemory()
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        instance = null
        (application as? SerifuApp)?.isServiceActive?.value = false
        stopSession()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        instance = null
        (application as? SerifuApp)?.isServiceActive?.value = false
        stopSession()
        super.onDestroy()
    }

    private fun stopSession() {
        if (!started) return
        started = false
        isRunning = false
        instance = null
        (application as? SerifuApp)?.isServiceActive?.value = false
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
        screenReceiver = null
        if (::overlays.isInitialized) {
            overlays.detach()
        }
        ocr.close()
        if (::translator.isInitialized) {
            translator.close()
        }
        if (::capturer.isInitialized) {
            capturer.close()
        }
        sessionJob.cancel()
        loop = null
    }

    companion object {
        const val CHANNEL = "serifu_status"
        const val PERMISSION_NOTE = 21

        var isRunning: Boolean = false
            internal set
        var instance: SerifuAccessibilityService? = null
            internal set
    }
}
