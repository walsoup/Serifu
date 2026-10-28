package app.serifu.coordinator

import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.serifu.R
import app.serifu.capture.CaptureOutcome
import app.serifu.capture.CapturedFrame
import app.serifu.capture.ScreenCapturer
import app.serifu.core.Tuning
import app.serifu.core.capture.ShotFailure
import app.serifu.core.change.FrameFingerprint
import app.serifu.core.change.PageCache
import app.serifu.core.change.ScrollEstimator
import app.serifu.core.coordinator.CaptureFailure
import app.serifu.core.coordinator.CoordinatorState
import app.serifu.core.coordinator.Effect
import app.serifu.core.coordinator.Event
import app.serifu.core.coordinator.Phase
import app.serifu.core.coordinator.Reducer
import app.serifu.core.coordinator.ReducerConfig
import app.serifu.core.image.LumaImage
import app.serifu.core.model.PillSide
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.serifu.core.translate.EngineId
import app.serifu.core.vision.SelfDetection
import app.serifu.debug.PipelineTimings
import app.serifu.ml.OcrEngine
import app.serifu.ml.TranslationUnavailable
import app.serifu.overlay.HudModel
import app.serifu.overlay.RenderBubble
import app.serifu.overlay.RenderPage
import app.serifu.overlay.OverlayEvents
import app.serifu.overlay.OverlayManager
import app.serifu.overlay.PillPhase
import app.serifu.service.EventRouter
import app.serifu.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Serial effect runner for [Reducer]. State changes only on this main-thread loop.
 * A result whose generation is no longer current is kept off the canvas.
 */
class TranslationCoordinator(
    private val scope: CoroutineScope,
    private val context: Context,
    private val overlays: OverlayManager,
    private val capturer: ScreenCapturer,
    private val ocr: OcrEngine,
    private val pipeline: PagePipeline,
    private val presence: () -> EventRouter.State,
    private val writeSettings: ((Settings) -> Settings) -> Unit,
    private val clearObscuredFlag: () -> Unit,
    private val openApp: () -> Unit,
    private val notifyPermissionLost: () -> Unit,
    private val publishTimings: (PipelineTimings) -> Unit,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : OverlayEvents {
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val frames = HashMap<Long, CapturedFrame>()
    private val pages = LinkedHashMap<Long, RenderPage>()
    private val pageCache = PageCache<RenderPage>()
    private var prevTiny: LumaImage? = null
    private var prevQuarter: LumaImage? = null
    private var prevCaptureMs: Long = 0
    private var prevReaderPkg: String? = null
    private var lastShownPage: RenderPage? = null
    private var previousLines: List<String> = emptyList()
    private var nextFrameId = 1L
    private var nextPageId = 1L
    @Volatile
    private var state: CoordinatorState = CoordinatorState()
    private var settleJob: Job? = null
    private var workJob: Job? = null
    @Volatile
    private var settings = Settings()
    private var applied = Settings()
    private var hasSettings = false
    private var screenOn = true
    private var paused = false
    private var secureHintShown = false

    @Volatile
    private var shownTexts: List<String> = emptyList()
    private var selfDiscarded = 0

    var timings: PipelineTimings = PipelineTimings()
        private set

    val readerForeground: Boolean get() = presence().reader && screenOn

    fun start() = scope.launch {
        for (event in events) {
            val transition = Reducer.reduce(state, event, clock(), config())
            state = transition.state
            transition.effects.forEach(::run)
            syncChrome()
        }
    }

    fun send(event: Event) {
        events.trySend(event)
    }

    fun pushSettings(value: Settings) {
        if (hasSettings && value == applied) {
            syncChrome()
            return
        }
        val previous = settings
        settings = value
        applied = value
        val first = !hasSettings
        hasSettings = true
        if (first) {
            send(Event.SessionStarted)
            return
        }
        var signaled = false
        if (previous.mode != value.mode) {
            send(Event.ModeChanged)
            signaled = true
        }
        if (previous.sourceLanguage != value.sourceLanguage || previous.targetLanguage != value.targetLanguage) {
            send(Event.LanguageChanged)
            signaled = true
        }
        if (previous.quality != value.quality) {
            send(Event.EngineChanged)
            signaled = true
        }
        if (!signaled) syncChrome()
    }

    /** Hides on the calling thread and records how long that took. The reducer hide is a second, idempotent pass. */
    fun hideForGesture() {
        val started = System.nanoTime()
        overlays.canvas.hideImmediately()
        val elapsedUs = (System.nanoTime() - started) / 1_000
        timings = timings.copy(hideUs = elapsedUs, hideSamples = timings.hideSamples + 1)
        publishTimings(timings)
        Log.d(TAG, "hide=${elapsedUs / 1000.0}ms")
    }

    fun onScreenOff() {
        screenOn = false
        send(Event.ScreenOff)
    }

    fun onScreenOn() {
        screenOn = true
        send(Event.ScreenOn)
    }

    fun onConfigurationChanged() {
        overlays.reposition(settings.pillSide, settings.pillYFraction)
        send(Event.ConfigurationChanged)
    }

    override fun onPillTap() {
        if (overlays.isHudShowing) overlays.dismissHudImmediately()
        if (settings.mode == TriggerMode.OFF) {
            replaceSettings(settings.copy(mode = TriggerMode.AUTO))
            writeSettings { it.copy(mode = TriggerMode.AUTO) }
            send(Event.ModeChanged)
            return
        }
        send(Event.PillTap)
    }

    override fun onPillOutside() {
        hideForGesture()
        if (overlays.isHudShowing) overlays.dismissHudImmediately()
        clearObscuredFlag()
        send(Event.TouchDown)
    }

    override fun onPillLongPress() {
        if (overlays.isHudShowing) {
            overlays.dismissHud(animated = true)
            return
        }
        overlays.showHud(HudModel(settings.sourceLanguage, settings.mode, paused))
        send(Event.HudOpened)
    }

    override fun onPillDocked(side: PillSide, yFraction: Float) {
        val fraction = Tuning.snapPillYFraction(yFraction)
        replaceSettings(settings.copy(pillSide = side, pillYFraction = fraction))
        writeSettings { it.copy(pillSide = side, pillYFraction = fraction) }
    }

    override fun hapticsEnabled(): Boolean = settings.haptics

    override fun onSource(language: SourceLanguage) {
        overlays.pill.confirmHaptic()
        replaceSettings(settings.copy(sourceLanguage = language))
        writeSettings { it.copy(sourceLanguage = language) }
        if (overlays.isHudShowing) overlays.dismissHudImmediately()
        send(Event.LanguageChanged)
    }

    override fun onMode(mode: TriggerMode) {
        overlays.pill.confirmHaptic()
        if (mode == settings.mode) {
            if (overlays.isHudShowing) overlays.dismissHudImmediately()
            return
        }
        replaceSettings(settings.copy(mode = mode))
        writeSettings { it.copy(mode = mode) }
        if (overlays.isHudShowing) overlays.dismissHudImmediately()
        send(Event.ModeChanged)
    }

    override fun onPauseToggle() {
        overlays.pill.confirmHaptic()
        paused = !paused
        if (overlays.isHudShowing) overlays.dismissHudImmediately()
        send(Event.PauseChanged(paused))
    }

    override fun onPeek(held: Boolean) {
        overlays.canvas.peek(held)
    }

    override fun onOpenApp() {
        overlays.dismissHudImmediately()
        openApp()
    }

    override fun onHudDismissed() {
        send(Event.HudDismissed)
    }

    private fun replaceSettings(value: Settings) {
        settings = value
        applied = value
    }

    private fun config(): ReducerConfig = ReducerConfig(
        mode = settings.mode,
        paused = paused,
        obscured = presence().obscured,
        readerForeground = presence().reader,
        screenOn = screenOn,
        hudVisible = overlays.isHudShowing,
        settleDelayMs = settings.settleDelayMs.toLong(),
        minIntervalMs = capturer.minIntervalMs,
        lastScreenshotUptimeMs = capturer.lastScreenshotRequestUptimeMs,
    )

    private fun run(effect: Effect) {
        when (effect) {
            Effect.InvalidateGeneration -> {
                settleJob?.cancel()
                workJob?.cancel()
            }
            Effect.HideCanvas -> overlays.canvas.hideImmediately()
            Effect.CancelSettle -> settleJob?.cancel()
            is Effect.ScheduleSettle -> {
                settleJob?.cancel()
                val wait = (effect.deadline - clock()).coerceAtLeast(0)
                settleJob = scope.launch {
                    delay(wait)
                    send(Event.SettleElapsed)
                }
            }
            is Effect.Capture -> launchCapture(effect.generation, effect.notBefore)
            is Effect.Process -> {
                val generation = effect.generation
                val frameId = effect.frameId
                workJob = scope.launch(Dispatchers.Default) { runProcess(generation, frameId) }
            }
            is Effect.RecycleFrame -> takeFrame(effect.frameId)?.recycle()
            is Effect.ShowPage -> show(effect.pageId)
            is Effect.CrossfadeTo -> show(effect.pageId)
            is Effect.CachePage -> Unit
            Effect.ClearPageCache -> {
                synchronized(pages) { pages.clear() }
                pageCache.clear()
                prevTiny = null
                prevQuarter = null
                lastShownPage = null
                previousLines = emptyList()
                shownTexts = emptyList()
                overlays.canvas.clearPage()
            }
            Effect.NotifyPermissionLost -> {
                overlays.showTooltip(context.getString(R.string.tooltip_permission))
                notifyPermissionLost()
            }
            Effect.ShowSecureTooltip -> if (!secureHintShown) {
                secureHintShown = true
                overlays.showTooltip(context.getString(R.string.tooltip_secure))
            }
            Effect.ShowCaptureFailedTooltip ->
                overlays.showTooltip(context.getString(R.string.tooltip_capture_failed))
            is Effect.SetMode -> {
                if (settings.mode == effect.mode) return
                replaceSettings(settings.copy(mode = effect.mode))
                writeSettings { it.copy(mode = effect.mode) }
                send(Event.ModeChanged)
            }
            Effect.ShowPill -> overlays.setPillVisible(true)
            Effect.HidePill -> overlays.setPillVisible(false)
        }
    }

    private fun launchCapture(generation: Long, notBefore: Long) {
        workJob?.cancel()
        workJob = scope.launch {
            var orphan: CapturedFrame? = null
            try {
                when (val outcome = capturer.capture(notBefore) { generation == state.generation }) {
                    CaptureOutcome.Superseded -> Unit
                    is CaptureOutcome.Failed -> send(failureEvent(generation, outcome.failure))
                    is CaptureOutcome.Ready -> {
                        if (generation != state.generation) {
                            orphan = outcome.frame
                            return@launch
                        }
                        val frameId = keepFrame(outcome.frame)
                        send(Event.CaptureSucceeded(generation, frameId))
                    }
                }
            } finally {
                orphan?.recycle()
            }
        }
    }

    private suspend fun runProcess(generation: Long, frameId: Long) {
        var frame = takeFrame(frameId) ?: return
        var recaptured = false
        val started = clock()
        try {
            while (true) {
                if (generation != state.generation) return
                if (frame.secureBlack) {
                    send(Event.FrameIsSecureBlack(generation))
                    return
                }
                val frameW = frame.bitmap.width
                val frameH = frame.bitmap.height
                val topInset = frame.systemBars.top
                val bottomInset = frame.systemBars.bottom
                val readerPkg = presence().foregroundPackage
                if (readerPkg != prevReaderPkg) {
                    pageCache.clear()
                    prevTiny = null
                    prevQuarter = null
                    prevReaderPkg = readerPkg
                }
                var cacheResult = "miss"
                val fpStarted = clock()
                val fingerprint =
                    FrameFingerprint.of(frame.luma.tiny, frameW, frameH, topInset, bottomInset)
                val fingerprintMs = (clock() - fpStarted).coerceAtLeast(0)
                val exact = pageCache.get(
                    fingerprint,
                    frame.rotation,
                    settings.sourceLanguage,
                    settings.targetLanguage,
                    EngineId.MLKIT,
                )
                if (exact != null) {
                    if (generation != state.generation) return
                    timings = timings.copy(
                        captureMs = (fpStarted - started).coerceAtLeast(0),
                        fingerprintMs = fingerprintMs,
                        ocrMs = 0,
                        clusterMs = 0,
                        translateMlKitMs = 0,
                        layoutMs = 0,
                        totalMs = (clock() - started).coerceAtLeast(0),
                        cacheResult = "exact",
                        selfDiscarded = selfDiscarded,
                    )
                    publishTimings(timings)
                    Log.d(TAG, "cache=exact total=${timings.totalMs}ms")
                    snapshotPrev(frame)
                    send(Event.PageReady(generation, rememberPage(exact)))
                    return
                }
                if (!recaptured && presence().reader) {
                    val prevT = prevTiny
                    val prevQ = prevQuarter
                    if (prevT != null && prevQ != null &&
                        clock() - prevCaptureMs < Tuning.SCROLL_REUSE_WINDOW_MS
                    ) {
                        val estimate = ScrollEstimator.estimate(
                            prevT, frame.luma.tiny, prevQ, frame.luma.quarter,
                            frameH, topInset, bottomInset,
                        )
                        if (estimate != null && generation == state.generation) {
                            val base = lastShownPage
                            if (base != null) {
                                val preview = shiftPage(base, estimate.dyPx, frameW, frameH)
                                if (preview != null) {
                                    cacheResult = "shift dy=${estimate.dyPx}"
                                    send(Event.PageReady(generation, rememberPage(preview)))
                                }
                            }
                        }
                    }
                }
                val ocrStarted = clock()
                val lines = try {
                    ocr.recognize(frame.bitmap, settings.sourceLanguage)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    send(Event.CaptureFailed(generation, CaptureFailure.OTHER))
                    return
                }
                val ocrDone = clock()
                if (generation != state.generation) return
                if (!recaptured && SelfDetection.leaked(lines, shownTexts)) {
                    recaptured = true
                    selfDiscarded += 1
                    frame.recycle()
                    val notBefore = capturer.lastScreenshotRequestUptimeMs + capturer.minIntervalMs
                    when (val outcome = capturer.capture(notBefore) { generation == state.generation }) {
                        CaptureOutcome.Superseded -> return
                        is CaptureOutcome.Failed -> {
                            send(failureEvent(generation, outcome.failure))
                            return
                        }
                        is CaptureOutcome.Ready -> frame = outcome.frame
                    }
                    continue
                }
                val lettered = try {
                    pipeline.letter(
                        lines = lines,
                        frame = frame,
                        settings = settings,
                        pill = overlays.pillRectOnScreen(),
                        previousLines = previousLines,
                        now = clock,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (missing: TranslationUnavailable) {
                    if (generation == state.generation) {
                        withContext(Dispatchers.Main) {
                            overlays.showTooltip(context.getString(R.string.tooltip_model, missing.languageName))
                        }
                        send(Event.PageEmpty(generation))
                    }
                    return
                } catch (_: Throwable) {
                    send(Event.CaptureFailed(generation, CaptureFailure.OTHER))
                    return
                }
                val finished = clock()
                if (generation != state.generation) return
                val page = lettered.page
                timings = timings.copy(
                    captureMs = (ocrStarted - started).coerceAtLeast(0),
                    fingerprintMs = fingerprintMs,
                    ocrMs = (ocrDone - ocrStarted).coerceAtLeast(0),
                    clusterMs = lettered.clusterMs,
                    translateMlKitMs = lettered.translateMs,
                    layoutMs = lettered.layoutMs,
                    totalMs = (finished - started).coerceAtLeast(0),
                    cacheResult = cacheResult,
                    selfDiscarded = selfDiscarded,
                )
                publishTimings(timings)
                Log.d(
                    TAG,
                    "lines=${lines.size} bubbles=${page?.bubbles?.size ?: 0} ocr=${timings.ocrMs}ms " +
                        "translate=${timings.translateMlKitMs}ms total=${timings.totalMs}ms cache=$cacheResult",
                )
                if (page == null) {
                    send(Event.PageEmpty(generation))
                } else {
                    pageCache.put(
                        fingerprint,
                        frame.rotation,
                        settings.sourceLanguage,
                        settings.targetLanguage,
                        EngineId.MLKIT,
                        page,
                    )
                    snapshotPrev(frame)
                    send(Event.PageReady(generation, rememberPage(page)))
                }
                return
            }
        } finally {
            frame.recycle()
        }
    }

    private fun show(pageId: Long) {
        val showing = state.phase as? Phase.Showing ?: return
        if (showing.pageId != pageId) return
        val page = synchronized(pages) { pages[pageId] } ?: return
        lastShownPage = page
        shownTexts = page.bubbles.map { it.layout.text.toString() }
        previousLines = page.sourceLines.take(Tuning.PREVIOUS_PAGE_CONTEXT_LINES)
        overlays.canvas.showPage(page, settings.debugOverlay)
    }

    private fun syncChrome() {
        val visible = presence().reader && screenOn
        overlays.setPillVisible(visible)
        if (!visible) return
        val mode = when {
            settings.mode == TriggerMode.OFF -> "off"
            paused -> "paused"
            settings.mode == TriggerMode.MANUAL -> "manual mode"
            else -> "auto mode"
        }
        overlays.applyPill(
            glyph = settings.sourceLanguage.seal,
            phase = pillPhase(),
            description = context.getString(
                R.string.pill_content_description,
                settings.sourceLanguage.englishName,
                mode,
            ),
            idleAlpha = settings.pillIdleAlpha,
            side = settings.pillSide,
            yFraction = settings.pillYFraction,
        )
    }

    private fun pillPhase(): PillPhase = when {
        settings.mode == TriggerMode.OFF -> PillPhase.OFF
        state.phase is Phase.Error -> PillPhase.ERROR
        state.phase is Phase.Capturing || state.phase is Phase.Processing -> PillPhase.PROCESSING
        paused -> PillPhase.PAUSED
        settings.mode == TriggerMode.MANUAL -> PillPhase.MANUAL
        else -> PillPhase.AUTO_READY
    }

    private fun keepFrame(frame: CapturedFrame): Long = synchronized(frames) {
        val id = nextFrameId++
        frames[id] = frame
        id
    }

    private fun snapshotPrev(frame: CapturedFrame) {
        prevTiny = LumaImage(
            frame.luma.tiny.width,
            frame.luma.tiny.height,
            frame.luma.tiny.bytes.copyOf(),
        )
        prevQuarter = LumaImage(
            frame.luma.quarter.width,
            frame.luma.quarter.height,
            frame.luma.quarter.bytes.copyOf(),
        )
        prevCaptureMs = clock()
    }

    /** Shift preview (plan.md 9.3): glue the previous lettering to the scrolled page. */
    private fun shiftPage(prev: RenderPage, dyPx: Int, frameW: Int, frameH: Int): RenderPage? {
        if (dyPx == 0) return prev
        val screen = android.graphics.RectF(0f, 0f, frameW.toFloat(), frameH.toFloat())
        val shifted = ArrayList<RenderBubble>(prev.bubbles.size)
        for (bubble in prev.bubbles) {
            if (bubble.patch.width() <= 0f || bubble.patch.height() <= 0f) continue
            val patch = android.graphics.RectF(bubble.patch)
            patch.offset(0f, dyPx.toFloat())
            val visible = android.graphics.RectF(patch)
            if (!visible.intersect(screen)) continue
            val fraction = visible.width() * visible.height() / (patch.width() * patch.height())
            if (fraction < Tuning.SHIFT_PREVIEW_ONSCREEN_FRACTION) continue
            shifted.add(
                RenderBubble(
                    id = bubble.id,
                    kind = bubble.kind,
                    patch = patch,
                    patchCornerPx = bubble.patchCornerPx,
                    fillColor = bubble.fillColor,
                    textColor = bubble.textColor,
                    layout = bubble.layout,
                    layoutOrigin = android.graphics.PointF(
                        bubble.layoutOrigin.x,
                        bubble.layoutOrigin.y + dyPx,
                    ),
                    rotationDeg = bubble.rotationDeg,
                    seamless = bubble.seamless,
                    outlineColor = bubble.outlineColor,
                    sourceTextForDebug = bubble.sourceTextForDebug,
                ),
            )
        }
        if (shifted.isEmpty()) return null
        return RenderPage(
            bubbles = shifted,
            frameW = prev.frameW,
            frameH = prev.frameH,
            rotation = prev.rotation,
            sourceLines = prev.sourceLines,
            debugLines = emptyList(),
        )
    }

    private fun takeFrame(id: Long): CapturedFrame? = synchronized(frames) { frames.remove(id) }

    private fun rememberPage(page: RenderPage): Long = synchronized(pages) {
        val id = nextPageId++
        pages[id] = page
        while (pages.size > Tuning.PAGE_CACHE_SIZE) {
            val eldest = pages.entries.firstOrNull()?.key ?: break
            pages.remove(eldest)
        }
        id
    }

    private fun failureEvent(generation: Long, failure: ShotFailure): Event = when (failure) {
        ShotFailure.INTERVAL_SHORT -> Event.CaptureFailed(generation, CaptureFailure.INTERVAL_SHORT)
        ShotFailure.INTERNAL -> Event.CaptureFailed(generation, CaptureFailure.INTERNAL)
        ShotFailure.NO_ACCESS -> Event.CaptureFailed(generation, CaptureFailure.NO_ACCESS)
        ShotFailure.SECURE_WINDOW -> Event.CaptureFailed(generation, CaptureFailure.SECURE)
        ShotFailure.OTHER -> Event.CaptureFailed(generation, CaptureFailure.OTHER)
    }

    private companion object {
        const val TAG = "Serifu"
    }
}
