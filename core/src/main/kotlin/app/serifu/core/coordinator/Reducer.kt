package app.serifu.core.coordinator

import app.serifu.core.Tuning
import app.serifu.core.model.TriggerMode

/**
 * Pure reading-loop transitions. Section 7.3 of the build spec is the authority;
 * the effect runner only performs what a [Transition] lists.
 */
object Reducer {
    fun reduce(state: CoordinatorState, event: Event, now: Long, config: ReducerConfig): Transition =
        when (event) {
            Event.TouchDown, Event.Scrolled, Event.VolumeKey, Event.WindowChanged ->
                onInteract(state, now, config)
            Event.SettleElapsed -> onSettleElapsed(state, now, config)
            Event.PillTap -> onPillTap(state, config)
            is Event.CaptureSucceeded -> onCaptureSucceeded(state, event)
            is Event.CaptureFailed -> onCaptureFailed(state, event, now, config)
            is Event.FrameIsSecureBlack -> onSecureFrame(state, event)
            is Event.PageReady -> onPageReady(state, event)
            is Event.PageEmpty -> onPageEmpty(state, event, now)
            is Event.UpgradeReady -> onUpgrade(state, event)
            is Event.ForegroundChanged -> onForeground(state, event, now, config)
            Event.ScreenOff -> goOff(state, config.copy(screenOn = false), clearCache = false)
            Event.ScreenOn -> onScreenOn(state, now, config.copy(screenOn = true))
            Event.ModeChanged -> onModeChanged(state, now, config)
            Event.LanguageChanged, Event.EngineChanged -> onReload(state, now, config)
            Event.ConfigurationChanged -> onConfiguration(state, now, config)
            Event.ContentChanged -> onContentChanged(state, now, config)
            is Event.ObscuredChanged -> onObscured(state, event, now, config.copy(obscured = event.obscured))
            is Event.PauseChanged -> onPause(state, event, now, config.copy(paused = event.paused))
            Event.HudOpened -> onHudOpened(state)
            Event.HudDismissed -> onHudDismissed(state, now, config.copy(hudVisible = false))
            Event.SessionStarted -> resume(state, now, config)
        }

    private fun onInteract(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (!config.readerForeground || !config.screenOn) {
            return goOff(state, config, clearCache = !config.readerForeground)
        }
        if (config.mode == TriggerMode.OFF) {
            return Transition(
                state.copy(phase = Phase.Off),
                listOf(Effect.HideCanvas, Effect.CancelSettle, Effect.ShowPill),
            )
        }
        val next = state.invalidated()
        return if (config.canAuto()) settling(next, now + config.settleDelayMs) else hold(next)
    }

    private fun onSettleElapsed(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (state.phase !is Phase.Settling) return stay(state)
        if (config.hudVisible && config.canAuto(ignoreHud = true)) return stay(state)
        if (!config.canAuto()) {
            if (!config.readerForeground || !config.screenOn || config.mode == TriggerMode.OFF) {
                return goOff(state, config, clearCache = !config.readerForeground)
            }
            return Transition(state.copy(phase = Phase.Interacting), listOf(Effect.CancelSettle))
        }
        val generation = state.generation + 1
        return Transition(
            state.copy(phase = Phase.Capturing, generation = generation, captureRetries = 0),
            listOf(Effect.Capture(generation, config.lastScreenshotUptimeMs + config.minIntervalMs)),
        )
    }

    private fun onPillTap(state: CoordinatorState, config: ReducerConfig): Transition {
        if (config.mode == TriggerMode.OFF) {
            return Transition(state, listOf(Effect.SetMode(TriggerMode.AUTO)))
        }
        if (!config.readerForeground || !config.screenOn || state.phase is Phase.Off) return stay(state)
        val generation = state.generation + 1
        return Transition(
            state.copy(
                phase = Phase.Capturing,
                generation = generation,
                captureRetries = 0,
                emptyRetries = 0,
                emptySinceMs = 0,
            ),
            listOf(
                Effect.InvalidateGeneration,
                Effect.HideCanvas,
                Effect.CancelSettle,
                Effect.Capture(generation, config.lastScreenshotUptimeMs + config.minIntervalMs),
            ),
        )
    }

    private fun onCaptureSucceeded(state: CoordinatorState, event: Event.CaptureSucceeded): Transition {
        if (state.phase is Phase.Capturing && event.generation == state.generation) {
            return Transition(
                state.copy(phase = Phase.Processing(event.frameId)),
                listOf(Effect.Process(event.generation, event.frameId)),
            )
        }
        return Transition(state, listOf(Effect.RecycleFrame(event.frameId)))
    }

    private fun onCaptureFailed(
        state: CoordinatorState,
        event: Event.CaptureFailed,
        now: Long,
        config: ReducerConfig,
    ): Transition {
        if (event.failure == CaptureFailure.NO_ACCESS) {
            return failed(state, ErrorKind.PERMISSION_LOST, Effect.NotifyPermissionLost)
        }
        if (event.failure == CaptureFailure.SECURE) {
            return failed(state, ErrorKind.SECURE_WINDOW, Effect.ShowSecureTooltip)
        }
        val awaiting = state.phase is Phase.Capturing || state.phase is Phase.Processing
        if (!awaiting || event.generation != state.generation) return stay(state)
        val delay = retryDelay(event.failure, state.captureRetries, config) ?: return failed(
            state,
            ErrorKind.CAPTURE_FAILED,
            Effect.ShowCaptureFailedTooltip,
        )
        return Transition(
            state.copy(phase = Phase.Capturing, captureRetries = state.captureRetries + 1),
            listOf(Effect.Capture(state.generation, now + delay)),
        )
    }

    private fun onSecureFrame(state: CoordinatorState, event: Event.FrameIsSecureBlack): Transition {
        if (state.phase !is Phase.Processing || event.generation != state.generation) return stay(state)
        val recycle = Effect.RecycleFrame(state.phase.frameId)
        return Transition(
            state.copy(phase = Phase.Error(ErrorKind.SECURE_WINDOW), captureRetries = 0),
            listOf(recycle, Effect.HideCanvas, Effect.CancelSettle, Effect.ShowSecureTooltip),
        )
    }

    private fun onPageReady(state: CoordinatorState, event: Event.PageReady): Transition {
        if ((state.phase is Phase.Processing || state.phase is Phase.Showing) && event.generation == state.generation) {
            return Transition(
                state.copy(
                    phase = Phase.Showing(event.pageId),
                    captureRetries = 0,
                    emptyRetries = 0,
                    emptySinceMs = 0,
                ),
                listOf(Effect.ShowPage(event.pageId)),
            )
        }
        return Transition(state, listOf(Effect.CachePage(event.pageId)))
    }

    private fun onPageEmpty(state: CoordinatorState, event: Event.PageEmpty, now: Long): Transition {
        if ((state.phase !is Phase.Processing && state.phase !is Phase.Showing) || event.generation != state.generation) return stay(state)
        return Transition(
            state.copy(phase = Phase.Idle, captureRetries = 0, emptySinceMs = now),
            listOf(Effect.HideCanvas),
        )
    }

    private fun onUpgrade(state: CoordinatorState, event: Event.UpgradeReady): Transition {
        val showing = state.phase as? Phase.Showing
        if (showing == null || event.generation != state.generation) {
            return Transition(state, listOf(Effect.CachePage(event.pageId)))
        }
        return Transition(
            state.copy(phase = Phase.Showing(event.pageId)),
            listOf(Effect.CrossfadeTo(event.pageId)),
        )
    }

    private fun onForeground(
        state: CoordinatorState,
        event: Event.ForegroundChanged,
        now: Long,
        config: ReducerConfig,
    ): Transition {
        val cfg = config.copy(readerForeground = event.isReader, obscured = if (event.isReader) config.obscured else false)
        return if (event.isReader) onReaderForeground(state, now, cfg) else goOff(state, cfg, clearCache = true)
    }

    private fun onReaderForeground(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (!config.screenOn) return goOff(state, config.copy(screenOn = false), clearCache = false)
        if (state.phase !is Phase.Off) {
            return Transition(state, listOf(Effect.ShowPill))
        }
        return resume(state, now, config)
    }

    private fun onScreenOn(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (state.phase !is Phase.Off) return Transition(state, if (config.readerForeground) listOf(Effect.ShowPill) else emptyList())
        return resume(state, now, config)
    }

    private fun onModeChanged(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (config.mode == TriggerMode.OFF) return goOff(state, config, clearCache = false)
        if (state.phase is Phase.Off) return resume(state, now, config)
        if (config.canAuto(ignoreHud = true)) return settling(state.invalidated(), now + config.settleDelayMs)
        if (config.readerForeground && config.screenOn) {
            if (state.phase is Phase.Showing) {
                return Transition(state.copy(captureRetries = 0), listOf(Effect.CancelSettle, Effect.ShowPill))
            }
            return hold(state.invalidated())
        }
        return goOff(state, config, clearCache = false)
    }

    private fun onReload(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (!config.readerForeground || !config.screenOn || config.mode == TriggerMode.OFF) {
            return goOff(state, config, clearCache = false)
        }
        if (config.canAuto(ignoreHud = true)) return settling(state.invalidated(), now)
        return Transition(
            state.invalidated().copy(phase = Phase.Idle),
            listOf(Effect.InvalidateGeneration, Effect.HideCanvas, Effect.CancelSettle),
        )
    }

    private fun onConfiguration(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        val dropped = state.invalidated()
        val base = mutableListOf(
            Effect.ClearPageCache,
            Effect.HideCanvas,
            Effect.InvalidateGeneration,
            Effect.CancelSettle,
        )
        if (!config.readerForeground || !config.screenOn || config.mode == TriggerMode.OFF) {
            if (config.readerForeground && config.screenOn) base += Effect.ShowPill else base += Effect.HidePill
            return Transition(dropped.copy(phase = Phase.Off), base)
        }
        if (config.canAuto(ignoreHud = true)) {
            val deadline = now + config.settleDelayMs
            base += Effect.ScheduleSettle(deadline)
            return Transition(dropped.copy(phase = Phase.Settling(deadline)), base)
        }
        return Transition(dropped.copy(phase = Phase.Idle), base)
    }

    private fun onContentChanged(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (state.emptySinceMs == 0L) return stay(state)
        if (now - state.emptySinceMs > Tuning.EMPTY_PAGE_RETRY_WINDOW_MS) return stay(state)
        if (state.emptyRetries >= Tuning.EMPTY_PAGE_RETRY_MAX) return stay(state)
        if (state.phase !is Phase.Idle) return stay(state)
        if (!config.canAuto()) return stay(state)
        val deadline = now + Tuning.emptyPageRetryDelayMs(state.emptyRetries)
        return Transition(
            state.copy(phase = Phase.Settling(deadline), emptyRetries = state.emptyRetries + 1),
            listOf(Effect.ScheduleSettle(deadline)),
        )
    }

    private fun onObscured(state: CoordinatorState, event: Event.ObscuredChanged, now: Long, config: ReducerConfig): Transition {
        if (event.obscured) {
            if (state.phase is Phase.Off) {
                return Transition(state, listOf(Effect.HideCanvas, Effect.CancelSettle))
            }
            return hold(state.invalidated())
        }
        if ((state.phase is Phase.Interacting || state.phase is Phase.Idle) && config.canAuto()) {
            return settling(state, now + config.settleDelayMs, invalidate = false)
        }
        return stay(state)
    }

    private fun onPause(state: CoordinatorState, event: Event.PauseChanged, now: Long, config: ReducerConfig): Transition {
        if (event.paused) {
            return when (state.phase) {
                is Phase.Settling, is Phase.Capturing, is Phase.Processing -> hold(state.invalidated())
                is Phase.Showing -> Transition(state, listOf(Effect.CancelSettle))
                else -> stay(state)
            }
        }
        if ((state.phase is Phase.Idle || state.phase is Phase.Interacting) && config.canAuto()) {
            return settling(state, now + config.settleDelayMs, invalidate = false)
        }
        return stay(state)
    }

    private fun onHudOpened(state: CoordinatorState): Transition =
        if (state.phase is Phase.Settling) Transition(state, listOf(Effect.CancelSettle)) else stay(state)

    private fun onHudDismissed(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (!config.canAuto()) return stay(state)
        if (state.phase is Phase.Off || state.phase is Phase.Capturing || state.phase is Phase.Processing) {
            return stay(state)
        }
        return settling(state, now + config.settleDelayMs, invalidate = false)
    }

    private fun resume(state: CoordinatorState, now: Long, config: ReducerConfig): Transition {
        if (!config.screenOn || !config.readerForeground) {
            return Transition(state.copy(phase = Phase.Off), listOf(Effect.HideCanvas, Effect.HidePill))
        }
        if (config.mode == TriggerMode.OFF) {
            return Transition(state.copy(phase = Phase.Off), listOf(Effect.HideCanvas, Effect.ShowPill))
        }
        if (config.canAuto()) return settling(state, now + config.settleDelayMs, invalidate = false, showPill = true)
        return Transition(
            state.copy(phase = Phase.Idle, captureRetries = 0),
            listOf(Effect.ShowPill, Effect.CancelSettle),
        )
    }

    private fun goOff(state: CoordinatorState, config: ReducerConfig, clearCache: Boolean): Transition {
        val effects = buildList {
            add(Effect.InvalidateGeneration)
            add(Effect.HideCanvas)
            add(Effect.CancelSettle)
            if (clearCache) add(Effect.ClearPageCache)
            if (config.readerForeground && config.screenOn) add(Effect.ShowPill) else add(Effect.HidePill)
        }
        return Transition(state.invalidated().copy(phase = Phase.Off), effects)
    }

    private fun settling(
        state: CoordinatorState,
        deadline: Long,
        invalidate: Boolean = true,
        showPill: Boolean = false,
    ): Transition {
        val effects = buildList {
            if (invalidate) add(Effect.InvalidateGeneration)
            add(Effect.HideCanvas)
            add(Effect.CancelSettle)
            add(Effect.ScheduleSettle(deadline))
            if (showPill) add(Effect.ShowPill)
        }
        return Transition(state.copy(phase = Phase.Settling(deadline)), effects)
    }

    private fun hold(state: CoordinatorState): Transition = Transition(
        state.copy(phase = Phase.Interacting),
        listOf(Effect.InvalidateGeneration, Effect.HideCanvas, Effect.CancelSettle),
    )

    private fun failed(state: CoordinatorState, kind: ErrorKind, notice: Effect): Transition = Transition(
        state.copy(phase = Phase.Error(kind), captureRetries = 0),
        listOf(Effect.HideCanvas, Effect.CancelSettle, notice),
    )

    private fun retryDelay(failure: CaptureFailure, retriesUsed: Int, config: ReducerConfig): Long? {
        if (retriesUsed >= Tuning.CAPTURE_MAX_RETRIES) return null
        return when (failure) {
            CaptureFailure.INTERVAL_SHORT -> config.minIntervalMs
            CaptureFailure.INTERNAL -> Tuning.CAPTURE_INTERNAL_ERROR_RETRY_MS
            CaptureFailure.NO_ACCESS, CaptureFailure.SECURE, CaptureFailure.OTHER -> null
        }
    }

    private fun stay(state: CoordinatorState) = Transition(state, emptyList())

    private fun CoordinatorState.invalidated() = copy(
        generation = generation + 1,
        captureRetries = 0,
        emptyRetries = 0,
        emptySinceMs = 0,
    )
}
