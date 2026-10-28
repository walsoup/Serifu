package app.serifu.core.coordinator

import app.serifu.core.model.TriggerMode

sealed interface Effect {
    data object InvalidateGeneration : Effect
    data object HideCanvas : Effect
    data object CancelSettle : Effect
    data class ScheduleSettle(val deadline: Long) : Effect
    data class Capture(val generation: Long, val notBefore: Long) : Effect
    data class Process(val generation: Long, val frameId: Long) : Effect
    data class RecycleFrame(val frameId: Long) : Effect
    data class ShowPage(val pageId: Long) : Effect
    data class CrossfadeTo(val pageId: Long) : Effect
    data class CachePage(val pageId: Long) : Effect
    data object ClearPageCache : Effect
    data object NotifyPermissionLost : Effect
    data object ShowSecureTooltip : Effect
    data object ShowCaptureFailedTooltip : Effect
    data class SetMode(val mode: TriggerMode) : Effect
    data object ShowPill : Effect
    data object HidePill : Effect
}

data class Transition(
    val state: CoordinatorState,
    val effects: List<Effect>,
)
