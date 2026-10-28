package app.serifu.core.coordinator

enum class CaptureFailure {
    INTERVAL_SHORT,
    INTERNAL,
    NO_ACCESS,
    SECURE,
    OTHER,
}

sealed interface Event {
    data object TouchDown : Event
    data object Scrolled : Event
    data object VolumeKey : Event
    data object WindowChanged : Event
    data object SettleElapsed : Event
    data object PillTap : Event
    data class CaptureSucceeded(val generation: Long, val frameId: Long) : Event
    data class CaptureFailed(val generation: Long, val failure: CaptureFailure) : Event
    data class FrameIsSecureBlack(val generation: Long) : Event
    data class PageReady(val generation: Long, val pageId: Long) : Event
    data class PageEmpty(val generation: Long) : Event
    data class UpgradeReady(val generation: Long, val pageId: Long) : Event
    data class ForegroundChanged(val isReader: Boolean) : Event
    data object ScreenOff : Event
    data object ScreenOn : Event
    data object ModeChanged : Event
    data object LanguageChanged : Event
    data object EngineChanged : Event
    data object ConfigurationChanged : Event
    data object ContentChanged : Event
    data class ObscuredChanged(val obscured: Boolean) : Event
    data class PauseChanged(val paused: Boolean) : Event
    data object HudOpened : Event
    data object HudDismissed : Event
    data object SessionStarted : Event
}
