package app.walsoup.serifu.service

import android.view.accessibility.AccessibilityEvent

class EventRouter {
    data class State(
        val foregroundPackage: String? = null,
        val obscured: Boolean = false,
        val reader: Boolean = false,
    )

    fun reduce(
        state: State,
        eventType: Int,
        packageName: String?,
        className: String?,
        readers: Set<String>,
        ownPackage: String,
        imePackage: String?,
    ): State {
        val pkg = packageName ?: return state
        if (pkg == ownPackage) {
            val name = className.orEmpty()
            return when {
                // Test page is a reader. The companion activity is not.
                // Overlay windows share the package and must not steal foreground.
                name.endsWith("TestPageActivity") ->
                    state.copy(foregroundPackage = pkg, obscured = false, reader = true)
                name.endsWith("MainActivity") ->
                    state.copy(foregroundPackage = pkg, obscured = false, reader = false)
                else -> state
            }
        }
        if (pkg == SYSTEM_UI || (imePackage != null && pkg == imePackage)) {
            return state.copy(obscured = true)
        }
        if (pkg in readers) {
            return state.copy(foregroundPackage = pkg, obscured = false, reader = true)
        }
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return state.copy(foregroundPackage = pkg, obscured = false, reader = false)
        }
        return state
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
    }
}
