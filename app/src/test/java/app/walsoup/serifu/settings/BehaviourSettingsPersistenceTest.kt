package app.walsoup.serifu.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import app.serifu.core.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The Behaviour capture-delay row is a restricted setting: the slider snaps
 * to 50 ms steps and the codec snaps again on write and read. Leaving the
 * screen and coming back must show the persisted step value, not the raw
 * gesture value.
 */
class BehaviourSettingsPersistenceTest {
    @Test
    fun restrictedValueIsSnappedByCodecOnWriteAndRead() {
        val requested = 455
        val expected = Tuning.snapSettleDelay(requested)
        assertEquals("455 ms is not an allowed step", 450, expected)

        val prefs = mutablePreferencesOf()
        Settings(settleDelayMs = requested).writeTo(prefs)

        val reopened = prefs.toSettings(defaultReaders = emptySet())
        assertEquals(expected, reopened.settleDelayMs)
        assertNotEquals(requested, reopened.settleDelayMs)
    }

    @Test
    fun leaveAndReturnKeepsTheSnappedValue() {
        val furthest = Tuning.SETTLE_DELAY_MAX_MS
        val expected = Tuning.snapSettleDelay(furthest)
        assertEquals("the slider's furthest position is already a step", furthest, expected)

        val prefs = mutablePreferencesOf()
        Settings(settleDelayMs = expected).writeTo(prefs)

        val firstRead = prefs.toSettings(defaultReaders = emptySet())
        val afterReturn = prefs.toSettings(defaultReaders = emptySet())

        assertEquals(expected, firstRead.settleDelayMs)
        assertEquals("a re-created view model reads the persisted value", expected, afterReturn.settleDelayMs)
        assertEquals(
            "the value shown by settle_label is a legal step",
            0,
            (afterReturn.settleDelayMs - Tuning.SETTLE_DELAY_MIN_MS) % Tuning.SETTLE_DELAY_STEP_MS,
        )
    }
}
