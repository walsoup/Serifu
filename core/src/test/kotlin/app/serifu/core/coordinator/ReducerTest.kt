package app.serifu.core.coordinator

import app.serifu.core.Tuning
import app.serifu.core.model.TriggerMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReducerTest {
    @Test
    fun autoInteractionsSettleAndExtendTheDeadline() {
        val idle = CoordinatorState(phase = Phase.Idle, generation = 4)
        val first = Reducer.reduce(idle, Event.TouchDown, now = 1_000, auto())
        assertEquals(Phase.Settling(1_450), first.state.phase)
        assertEquals(5, first.state.generation)
        assertEquals(
            listOf(
                Effect.InvalidateGeneration,
                Effect.HideCanvas,
                Effect.CancelSettle,
                Effect.ScheduleSettle(1_450),
            ),
            first.effects,
        )
        val extended = Reducer.reduce(first.state, Event.Scrolled, now = 1_200, auto())
        assertEquals(Phase.Settling(1_650), extended.state.phase)
        assertEquals(6, extended.state.generation)
        assertEquals(Effect.ScheduleSettle(1_650), extended.effects.last())
    }

    @Test
    fun volumeAndWindowFollowTheSameAutoRule() {
        val showing = CoordinatorState(phase = Phase.Showing(3), generation = 2)
        for (event in listOf(Event.VolumeKey, Event.WindowChanged)) {
            val next = Reducer.reduce(showing, event, now = 50, auto(settleDelayMs = 250))
            assertEquals(Phase.Settling(300), next.state.phase)
            assertEquals(3, next.state.generation)
            assertTrue(next.effects.contains(Effect.HideCanvas))
            assertTrue(next.effects.contains(Effect.ScheduleSettle(300)))
        }
    }

    @Test
    fun manualPausedOrObscuredHoldsWithoutACapture() {
        val showing = CoordinatorState(phase = Phase.Showing(1), generation = 2, emptyRetries = 1, emptySinceMs = 9)
        val cases = listOf(manual(), auto(paused = true), auto(obscured = true))
        for (config in cases) {
            val next = Reducer.reduce(showing, Event.TouchDown, now = 10, config)
            assertEquals(Phase.Interacting, next.state.phase)
            assertEquals(0, next.state.emptyRetries)
            assertEquals(0, next.state.emptySinceMs)
            assertEquals(
                listOf(Effect.InvalidateGeneration, Effect.HideCanvas, Effect.CancelSettle),
                next.effects,
            )
            assertFalse(next.effects.any { it is Effect.ScheduleSettle || it is Effect.Capture })
        }
    }

    @Test
    fun settleElapsedCapturesOnTheApiCooldown() {
        val settling = CoordinatorState(phase = Phase.Settling(500), generation = 7)
        val oldApi = Reducer.reduce(
            settling,
            Event.SettleElapsed,
            now = 500,
            auto(minIntervalMs = Tuning.minScreenshotIntervalMs(31), lastScreenshotUptimeMs = 10_000),
        )
        assertEquals(Phase.Capturing, oldApi.state.phase)
        assertEquals(8, oldApi.state.generation)
        assertEquals(listOf(Effect.Capture(8, 11_050)), oldApi.effects)

        val newApi = Reducer.reduce(
            settling,
            Event.SettleElapsed,
            now = 500,
            auto(minIntervalMs = Tuning.minScreenshotIntervalMs(34), lastScreenshotUptimeMs = 10_000),
        )
        assertEquals(listOf(Effect.Capture(8, 10_350)), newApi.effects)
    }

    @Test
    fun settleDoesNotFireWhileObscuredPausedOrTheHudIsOpen() {
        val settling = CoordinatorState(phase = Phase.Settling(100), generation = 1)
        val obscured = Reducer.reduce(settling, Event.SettleElapsed, now = 100, auto(obscured = true))
        assertEquals(Phase.Interacting, obscured.state.phase)
        assertEquals(listOf(Effect.CancelSettle), obscured.effects)

        val hud = Reducer.reduce(settling, Event.SettleElapsed, now = 100, auto(hudVisible = true))
        assertEquals(settling, hud.state)
        assertEquals(emptyList(), hud.effects)
    }

    @Test
    fun pillTapScansNowAndOffSwitchesToAuto() {
        val config = auto(minIntervalMs = 350, lastScreenshotUptimeMs = 1_000)
        val from = listOf(
            Phase.Idle,
            Phase.Interacting,
            Phase.Showing(4),
            Phase.Settling(10),
            Phase.Error(ErrorKind.CAPTURE_FAILED),
        )
        for (phase in from) {
            val next = Reducer.reduce(CoordinatorState(phase = phase, generation = 2), Event.PillTap, now = 2_000, config)
            assertEquals(Phase.Capturing, next.state.phase)
            assertEquals(3, next.state.generation)
            assertEquals(
                listOf(
                    Effect.InvalidateGeneration,
                    Effect.HideCanvas,
                    Effect.CancelSettle,
                    Effect.Capture(3, 1_350),
                ),
                next.effects,
            )
        }
        val off = Reducer.reduce(
            CoordinatorState(phase = Phase.Off),
            Event.PillTap,
            now = 0,
            auto(mode = TriggerMode.OFF),
        )
        assertEquals(Phase.Off, off.state.phase)
        assertEquals(listOf(Effect.SetMode(TriggerMode.AUTO)), off.effects)
    }

    @Test
    fun captureSuccessProcessesAndAStaleFrameIsRecycled() {
        val capturing = CoordinatorState(phase = Phase.Capturing, generation = 5)
        val fresh = Reducer.reduce(capturing, Event.CaptureSucceeded(5, frameId = 9), now = 0, auto())
        assertEquals(Phase.Processing(9), fresh.state.phase)
        assertEquals(5, fresh.state.generation)
        assertEquals(listOf(Effect.Process(5, 9)), fresh.effects)

        val stale = Reducer.reduce(capturing, Event.CaptureSucceeded(4, frameId = 8), now = 0, auto())
        assertEquals(capturing, stale.state)
        assertEquals(listOf(Effect.RecycleFrame(8)), stale.effects)
    }

    @Test
    fun intervalAndInternalFailuresRetryOnceThenError() {
        val capturing = CoordinatorState(phase = Phase.Capturing, generation = 3)
        val interval = Reducer.reduce(
            capturing,
            Event.CaptureFailed(3, CaptureFailure.INTERVAL_SHORT),
            now = 5_000,
            auto(minIntervalMs = 350),
        )
        assertEquals(Phase.Capturing, interval.state.phase)
        assertEquals(1, interval.state.captureRetries)
        assertEquals(listOf(Effect.Capture(3, 5_350)), interval.effects)
        val intervalGaveUp = Reducer.reduce(
            interval.state,
            Event.CaptureFailed(3, CaptureFailure.INTERVAL_SHORT),
            now = 5_400,
            auto(minIntervalMs = 350),
        )
        assertEquals(Phase.Error(ErrorKind.CAPTURE_FAILED), intervalGaveUp.state.phase)
        assertEquals(
            listOf(Effect.HideCanvas, Effect.CancelSettle, Effect.ShowCaptureFailedTooltip),
            intervalGaveUp.effects,
        )

        val internal = Reducer.reduce(
            capturing,
            Event.CaptureFailed(3, CaptureFailure.INTERNAL),
            now = 8_000,
            auto(),
        )
        assertEquals(listOf(Effect.Capture(3, 8_500)), internal.effects)
        val internalGaveUp = Reducer.reduce(
            internal.state,
            Event.CaptureFailed(3, CaptureFailure.INTERNAL),
            now = 8_600,
            auto(),
        )
        assertEquals(Phase.Error(ErrorKind.CAPTURE_FAILED), internalGaveUp.state.phase)

        val ignored = Reducer.reduce(
            capturing,
            Event.CaptureFailed(2, CaptureFailure.INTERVAL_SHORT),
            now = 0,
            auto(),
        )
        assertEquals(capturing, ignored.state)
        assertEquals(emptyList(), ignored.effects)
    }

    @Test
    fun permissionLossAndSecureWindowAreFatal() {
        val idle = CoordinatorState(phase = Phase.Idle, generation = 1)
        val lost = Reducer.reduce(idle, Event.CaptureFailed(9, CaptureFailure.NO_ACCESS), now = 0, auto())
        assertEquals(Phase.Error(ErrorKind.PERMISSION_LOST), lost.state.phase)
        assertEquals(
            listOf(Effect.HideCanvas, Effect.CancelSettle, Effect.NotifyPermissionLost),
            lost.effects,
        )
        val processing = CoordinatorState(phase = Phase.Processing(4), generation = 6)
        val secure = Reducer.reduce(processing, Event.FrameIsSecureBlack(6), now = 0, auto())
        assertEquals(Phase.Error(ErrorKind.SECURE_WINDOW), secure.state.phase)
        assertEquals(
            listOf(
                Effect.RecycleFrame(4),
                Effect.HideCanvas,
                Effect.CancelSettle,
                Effect.ShowSecureTooltip,
            ),
            secure.effects,
        )
        val staleSecure = Reducer.reduce(processing, Event.FrameIsSecureBlack(5), now = 0, auto())
        assertEquals(processing, staleSecure.state)
    }

    @Test
    fun pageReadyShowsAndAStaleGenerationIsCachedInstead() {
        val processing = CoordinatorState(phase = Phase.Processing(1), generation = 4, emptyRetries = 2, emptySinceMs = 3)
        val ready = Reducer.reduce(processing, Event.PageReady(4, pageId = 20), now = 0, auto())
        assertEquals(Phase.Showing(20), ready.state.phase)
        assertEquals(0, ready.state.emptyRetries)
        assertEquals(listOf(Effect.ShowPage(20)), ready.effects)

        val scrolled = Reducer.reduce(processing, Event.Scrolled, now = 10, auto())
        val late = Reducer.reduce(scrolled.state, Event.PageReady(4, pageId = 20), now = 11, auto())
        assertEquals(scrolled.state, late.state)
        assertEquals(listOf(Effect.CachePage(20)), late.effects)
        assertFalse(late.state.phase is Phase.Showing)
    }

    @Test
    fun upgradeCrossfadesOnlyTheCurrentPage() {
        val showing = CoordinatorState(phase = Phase.Showing(2), generation = 4)
        val upgrade = Reducer.reduce(showing, Event.UpgradeReady(4, pageId = 5), now = 0, auto())
        assertEquals(Phase.Showing(5), upgrade.state.phase)
        assertEquals(4, upgrade.state.generation)
        assertEquals(listOf(Effect.CrossfadeTo(5)), upgrade.effects)
        val stale = Reducer.reduce(showing, Event.UpgradeReady(3, pageId = 9), now = 0, auto())
        assertEquals(showing, stale.state)
        assertEquals(listOf(Effect.CachePage(9)), stale.effects)
    }

    @Test
    fun emptyPageRetriesTwiceInsideTheWindow() {
        val processing = CoordinatorState(phase = Phase.Processing(1), generation = 4)
        val empty = Reducer.reduce(processing, Event.PageEmpty(4), now = 10_000, auto())
        assertEquals(Phase.Idle, empty.state.phase)
        assertEquals(10_000, empty.state.emptySinceMs)
        assertEquals(0, empty.state.emptyRetries)

        val first = Reducer.reduce(empty.state, Event.ContentChanged, now = 10_100, auto())
        assertEquals(Phase.Settling(10_900), first.state.phase)
        assertEquals(1, first.state.emptyRetries)
        assertEquals(listOf(Effect.ScheduleSettle(10_900)), first.effects)

        val ignoredWhileSettling = Reducer.reduce(first.state, Event.ContentChanged, now = 10_200, auto())
        assertEquals(first.state, ignoredWhileSettling.state)

        val captured = Reducer.reduce(first.state, Event.SettleElapsed, now = 10_900, auto(lastScreenshotUptimeMs = 0))
        val again = Reducer.reduce(
            captured.state,
            Event.CaptureSucceeded(captured.state.generation, frameId = 2),
            now = 10_950,
            auto(),
        )
        val stillEmpty = Reducer.reduce(
            again.state,
            Event.PageEmpty(again.state.generation),
            now = 11_000,
            auto(),
        )
        assertEquals(1, stillEmpty.state.emptyRetries)
        val second = Reducer.reduce(stillEmpty.state, Event.ContentChanged, now = 11_100, auto())
        assertEquals(Phase.Settling(12_700), second.state.phase)
        assertEquals(2, second.state.emptyRetries)

        val budgetSpent = Reducer.reduce(
            stillEmpty.state.copy(phase = Phase.Idle, emptyRetries = 2, emptySinceMs = 11_000),
            Event.ContentChanged,
            now = 11_200,
            auto(),
        )
        assertEquals(Phase.Idle, budgetSpent.state.phase)
        assertEquals(emptyList(), budgetSpent.effects)

        val tooLate = Reducer.reduce(
            empty.state,
            Event.ContentChanged,
            now = 10_000 + Tuning.EMPTY_PAGE_RETRY_WINDOW_MS + 1,
            auto(),
        )
        assertEquals(empty.state, tooLate.state)

        val scrolled = Reducer.reduce(empty.state, Event.Scrolled, now = 10_050, auto())
        val afterScroll = Reducer.reduce(scrolled.state.copy(phase = Phase.Idle), Event.ContentChanged, now = 10_080, auto())
        assertEquals(emptyList(), afterScroll.effects)
    }

    @Test
    fun obscuredBlocksAutoCaptureUntilItClears() {
        val showing = CoordinatorState(phase = Phase.Showing(1), generation = 2)
        val blocked = Reducer.reduce(showing, Event.TouchDown, now = 0, auto(obscured = true))
        assertEquals(Phase.Interacting, blocked.state.phase)
        assertTrue(blocked.effects.none { it is Effect.ScheduleSettle })

        val stillBlocked = Reducer.reduce(
            blocked.state,
            Event.ObscuredChanged(obscured = false),
            now = 20,
            auto(obscured = true),
        )
        assertEquals(Phase.Settling(470), stillBlocked.state.phase)
        assertEquals(listOf(Effect.HideCanvas, Effect.CancelSettle, Effect.ScheduleSettle(470)), stillBlocked.effects)
    }

    @Test
    fun pauseCancelsAPendingSettleAndResumeStartsAnother() {
        val settling = CoordinatorState(phase = Phase.Settling(400), generation = 2)
        val paused = Reducer.reduce(settling, Event.PauseChanged(paused = true), now = 100, auto())
        assertEquals(Phase.Interacting, paused.state.phase)
        assertEquals(
            listOf(Effect.InvalidateGeneration, Effect.HideCanvas, Effect.CancelSettle),
            paused.effects,
        )
        val resumed = Reducer.reduce(paused.state, Event.PauseChanged(paused = false), now = 150, auto(paused = true))
        assertEquals(Phase.Settling(600), resumed.state.phase)
    }

    @Test
    fun modeOffLeavesEveryPhase() {
        val phases = listOf(
            Phase.Off,
            Phase.Idle,
            Phase.Interacting,
            Phase.Settling(10),
            Phase.Capturing,
            Phase.Processing(1),
            Phase.Showing(1),
            Phase.Error(ErrorKind.PERMISSION_LOST),
        )
        for (phase in phases) {
            val next = Reducer.reduce(
                CoordinatorState(phase = phase, generation = 3),
                Event.ModeChanged,
                now = 0,
                auto(mode = TriggerMode.OFF),
            )
            assertEquals(Phase.Off, next.state.phase, phase.toString())
            assertEquals(4, next.state.generation)
            assertTrue(next.effects.contains(Effect.InvalidateGeneration))
            assertTrue(next.effects.contains(Effect.HideCanvas))
            assertTrue(next.effects.contains(Effect.CancelSettle))
            assertTrue(next.effects.contains(Effect.ShowPill))
        }
    }

    @Test
    fun leavingTheReaderOrTheScreenTurnsOff() {
        val showing = CoordinatorState(phase = Phase.Showing(1), generation = 2)
        val left = Reducer.reduce(showing, Event.ForegroundChanged(isReader = false), now = 0, auto())
        assertEquals(Phase.Off, left.state.phase)
        assertTrue(left.effects.contains(Effect.HidePill))
        assertTrue(left.effects.contains(Effect.ClearPageCache))

        val asleep = Reducer.reduce(showing, Event.ScreenOff, now = 0, auto())
        assertEquals(Phase.Off, asleep.state.phase)
        assertTrue(asleep.effects.contains(Effect.HidePill))
        assertFalse(asleep.effects.contains(Effect.ClearPageCache))

        val back = Reducer.reduce(left.state, Event.ForegroundChanged(isReader = true), now = 80, auto())
        assertEquals(Phase.Settling(530), back.state.phase)
        assertTrue(back.effects.contains(Effect.ShowPill))

        val awake = Reducer.reduce(
            CoordinatorState(phase = Phase.Off, generation = 1),
            Event.ScreenOn,
            now = 90,
            manual(),
        )
        assertEquals(Phase.Idle, awake.state.phase)
        assertTrue(awake.effects.contains(Effect.ShowPill))
    }

    @Test
    fun languageAndEngineReloadImmediatelyInAuto() {
        val showing = CoordinatorState(phase = Phase.Showing(1), generation = 8)
        for (event in listOf(Event.LanguageChanged, Event.EngineChanged)) {
            val autoReload = Reducer.reduce(showing, event, now = 5_000, auto())
            assertEquals(Phase.Settling(5_000), autoReload.state.phase)
            assertEquals(9, autoReload.state.generation)
            assertEquals(Effect.ScheduleSettle(5_000), autoReload.effects.last())
            val manualReload = Reducer.reduce(showing, event, now = 5_000, manual())
            assertEquals(Phase.Idle, manualReload.state.phase)
            assertTrue(manualReload.effects.contains(Effect.HideCanvas))
            assertTrue(manualReload.effects.contains(Effect.InvalidateGeneration))
        }
    }

    @Test
    fun configurationClearsTheCacheAndResettles() {
        val showing = CoordinatorState(phase = Phase.Showing(1), generation = 2)
        val rotated = Reducer.reduce(showing, Event.ConfigurationChanged, now = 100, auto(settleDelayMs = 450))
        assertEquals(Phase.Settling(550), rotated.state.phase)
        assertEquals(3, rotated.state.generation)
        assertEquals(
            listOf(
                Effect.ClearPageCache,
                Effect.HideCanvas,
                Effect.InvalidateGeneration,
                Effect.CancelSettle,
                Effect.ScheduleSettle(550),
            ),
            rotated.effects,
        )
        val manualRotate = Reducer.reduce(showing, Event.ConfigurationChanged, now = 100, manual())
        assertEquals(Phase.Idle, manualRotate.state.phase)
        assertTrue(manualRotate.effects.contains(Effect.ClearPageCache))
    }

    @Test
    fun contentChangesDoNothingWithoutAnEmptyPage() {
        val showing = CoordinatorState(phase = Phase.Showing(1), generation = 1)
        val next = Reducer.reduce(showing, Event.ContentChanged, now = 0, auto())
        assertEquals(showing, next.state)
        assertEquals(emptyList(), next.effects)
    }

    @Test
    fun thirtyRapidScrollsNeverShowThePageTheyInterrupted() {
        var state = CoordinatorState(phase = Phase.Showing(1), generation = 1)
        repeat(30) { step ->
            val now = step * 10L
            val scrolled = Reducer.reduce(state, Event.Scrolled, now, auto())
            val late = Reducer.reduce(
                scrolled.state,
                Event.PageReady(generation = state.generation, pageId = 100L + step),
                now + 1,
                auto(),
            )
            assertEquals(scrolled.state, late.state)
            assertFalse(late.effects.any { it is Effect.ShowPage || it is Effect.CrossfadeTo })
            val dropped = Reducer.reduce(
                scrolled.state,
                Event.CaptureSucceeded(generation = state.generation, frameId = step.toLong()),
                now + 2,
                auto(),
            )
            assertFalse(dropped.state.phase is Phase.Processing)
            assertEquals(listOf(Effect.RecycleFrame(step.toLong())), dropped.effects)
            state = scrolled.state
        }
    }

    @Test
    fun repeatedScrollsExtendTheSettleDeadlineOnVirtualTime() = runTest {
        val harness = SettleHarness(this, auto(settleDelayMs = 450))
        harness.send(Event.TouchDown)
        assertEquals(Phase.Settling(450), harness.state.phase)
        advanceTimeBy(200)
        runCurrent()
        harness.send(Event.Scrolled)
        assertEquals(Phase.Settling(650), harness.state.phase)
        advanceTimeBy(449)
        runCurrent()
        assertTrue(harness.state.phase is Phase.Settling)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(Phase.Capturing, harness.state.phase)
        assertTrue(harness.effects.any { it is Effect.Capture })
    }

    private class SettleHarness(
        private val scope: TestScope,
        private val config: ReducerConfig,
    ) {
        var state = CoordinatorState(phase = Phase.Idle)
        val effects = mutableListOf<Effect>()
        private var settle: Job? = null

        fun send(event: Event) {
            val transition = Reducer.reduce(state, event, scope.currentTime, config)
            state = transition.state
            transition.effects.forEach { effect ->
                effects += effect
                when (effect) {
                    is Effect.ScheduleSettle -> {
                        settle?.cancel()
                        val wait = (effect.deadline - scope.currentTime).coerceAtLeast(0)
                        settle = scope.launch {
                            delay(wait)
                            send(Event.SettleElapsed)
                        }
                    }
                    Effect.CancelSettle -> settle?.cancel()
                    else -> Unit
                }
            }
        }
    }
}

private fun auto(
    mode: TriggerMode = TriggerMode.AUTO,
    paused: Boolean = false,
    obscured: Boolean = false,
    hudVisible: Boolean = false,
    settleDelayMs: Long = 450,
    minIntervalMs: Long = 350,
    lastScreenshotUptimeMs: Long = 0,
) = ReducerConfig(
    mode = mode,
    paused = paused,
    obscured = obscured,
    readerForeground = true,
    screenOn = true,
    hudVisible = hudVisible,
    settleDelayMs = settleDelayMs,
    minIntervalMs = minIntervalMs,
    lastScreenshotUptimeMs = lastScreenshotUptimeMs,
)

private fun manual() = auto(mode = TriggerMode.MANUAL)
