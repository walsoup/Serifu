package app.walsoup.serifu.service

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRouterTest {
    private val router = EventRouter()
    private val readers = setOf("app.mihon")

    @Test
    fun readerClearsObscured() {
        val obscured = EventRouter.State(foregroundPackage = "app.mihon", obscured = true, reader = true)
        val next = router.reduce(
            obscured,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            "app.mihon",
            "reader.ReaderActivity",
            readers,
            ownPackage = "app.walsoup.serifu",
            imePackage = "com.google.android.inputmethod.latin",
        )
        assertEquals("app.mihon", next.foregroundPackage)
        assertTrue(next.reader)
        assertFalse(next.obscured)
    }

    @Test
    fun systemUiAndImeObscureWithoutDroppingTheReader() {
        val reading = EventRouter.State(foregroundPackage = "app.mihon", reader = true)
        val shade = router.reduce(
            reading,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            "com.android.systemui",
            "Shade",
            readers,
            "app.walsoup.serifu",
            imePackage = "com.ime",
        )
        assertTrue(shade.obscured)
        assertTrue(shade.reader)
        assertEquals("app.mihon", shade.foregroundPackage)
        val ime = router.reduce(
            reading,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            "com.ime",
            "Ime",
            readers,
            "app.walsoup.serifu",
            imePackage = "com.ime",
        )
        assertTrue(ime.obscured)
        assertEquals("app.mihon", ime.foregroundPackage)
    }

    @Test
    fun companionHidesAndTestPageCountsAsAReader() {
        val reading = EventRouter.State(foregroundPackage = "app.mihon", reader = true)
        val home = router.reduce(
            reading,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            "app.walsoup.serifu",
            "app.walsoup.serifu.ui.MainActivity",
            readers,
            "app.walsoup.serifu",
            null,
        )
        assertFalse(home.reader)
        assertEquals("app.walsoup.serifu", home.foregroundPackage)
        val overlay = router.reduce(
            reading,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            "app.walsoup.serifu",
            "android.widget.FrameLayout",
            readers,
            "app.walsoup.serifu",
            null,
        )
        assertTrue(overlay.reader)
        assertEquals("app.mihon", overlay.foregroundPackage)
        val testPage = router.reduce(
            home,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            "app.walsoup.serifu",
            "app.walsoup.serifu.ui.testpage.TestPageActivity",
            readers,
            "app.walsoup.serifu",
            null,
        )
        assertTrue(testPage.reader)
    }

    @Test
    fun anotherAppOnWindowChangeIsNotAReader() {
        val reading = EventRouter.State(foregroundPackage = "app.mihon", reader = true)
        val next = router.reduce(
            reading,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            "com.android.settings",
            "Settings",
            readers,
            "app.walsoup.serifu",
            null,
        )
        assertFalse(next.reader)
        assertEquals("com.android.settings", next.foregroundPackage)
        val ignored = router.reduce(
            reading,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            "com.android.settings",
            "Settings",
            readers,
            "app.walsoup.serifu",
            null,
        )
        assertTrue(ignored.reader)
        assertEquals("app.mihon", ignored.foregroundPackage)
    }
}
