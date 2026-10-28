package app.serifu.service

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
import app.serifu.R
import app.serifu.SerifuApp
import app.serifu.capture.FrameConverter
import app.serifu.capture.ScreenCapturer
import app.serifu.coordinator.PagePipeline
import app.serifu.coordinator.TranslationCoordinator
import app.serifu.core.coordinator.Event
import app.serifu.ml.GemmaModelDownloader
import app.serifu.ml.GemmaTranslationEngine
import app.serifu.ml.MlKitTranslationEngine
import app.serifu.ml.ModelCatalog
import app.serifu.core.model.QualityMode
import app.serifu.ml.OcrEngine
import app.serifu.overlay.LetteringRenderer
import app.serifu.overlay.OverlayManager
import app.serifu.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class SerifuAccessibilityService : AccessibilityService() {
    private val router = EventRouter()
    private val overlays = OverlayManager(this)
    private var sessionJob = SupervisorJob()
    private var scope = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
    private var ocr = OcrEngine()
    private lateinit var translator: MlKitTranslationEngine
    private var gemma: GemmaTranslationEngine? = null
    private var capturer = ScreenCapturer(this, overlays, FrameConverter())
    private var loop: TranslationCoordinator? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var started = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (started) return
        if (!sessionJob.isActive) {
            sessionJob = SupervisorJob()
            scope = CoroutineScope(sessionJob + Dispatchers.Main.immediate)
            ocr = OcrEngine()
            capturer = ScreenCapturer(this, overlays, FrameConverter())
        }
        val app = application as SerifuApp
        translator = MlKitTranslationEngine(
            wifiOnly = { app.settingsRepository.settings.value?.wifiOnlyDownloads != false },
        )
        gemma = GemmaTranslationEngine(
            this,
            diskCacheEnabled = { app.settingsRepository.settings.value?.llmDiskCache != false },
        )
        val gemmaEngine = gemma
        val reading = TranslationCoordinator(
            scope = scope,
            context = this,
            overlays = overlays,
            capturer = capturer,
            ocr = ocr,
            pipeline = PagePipeline(this, translator, LetteringRenderer(this), ocr, gemma = gemmaEngine),
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
            notifyPermissionLost = ::notifyPermissionLost,
            publishTimings = { app.pipelineTimings.value = it },
        )
        reading.start()
        loop = reading
        overlays.events = reading
        overlays.attach()
        scope.launch {
            app.settingsRepository.settings.filterNotNull().collect { reading.pushSettings(it) }
        }
        scope.launch {
            // Auto-upgrade to progressive once the Gemma model lands on disk.
            val current = app.settingsRepository.settings.value
            if (current != null &&
                current.quality == QualityMode.FAST &&
                ModelCatalog.isComplete(this@SerifuAccessibilityService)
            ) {
                app.settingsRepository.update { it.copy(quality = QualityMode.BEST_PROGRESSIVE) }
            }
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
        val ownOverlay = incoming.packageName?.toString() == packageName &&
            !className.endsWith("MainActivity") &&
            !className.endsWith("TestPageActivity")
        val fromReader = incoming.packageName?.toString()?.let { pkg ->
            pkg in readers || (pkg == packageName && className.endsWith("TestPageActivity"))
        } == true
        when (incoming.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (fromReader) {
                reading.hideForGesture()
                reading.send(Event.Scrolled)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> if (!ownOverlay) {
                reading.hideForGesture()
                when {
                    !after.reader -> reading.send(Event.ForegroundChanged(isReader = false))
                    after.obscured -> reading.send(Event.ObscuredChanged(obscured = true))
                    !before.reader -> reading.send(Event.ForegroundChanged(isReader = true))
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

    override fun onUnbind(intent: Intent?): Boolean {
        stopSession()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopSession()
        super.onDestroy()
    }

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
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            gemma?.unloadNow()
            loop?.send(app.serifu.core.coordinator.Event.ConfigurationChanged)
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            ocr.close()
            ocr = OcrEngine()
            translator.close()
        }
    }

    private fun stopSession() {
        if (!started) return
        started = false
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
        screenReceiver = null
        overlays.detach()
        ocr.close()
        translator.close()
        gemma?.close()
        gemma = null
        capturer.close()
        sessionJob.cancel()
        loop = null
    }

    private companion object {
        const val CHANNEL = "serifu_status"
        const val PERMISSION_NOTE = 21
    }
}
