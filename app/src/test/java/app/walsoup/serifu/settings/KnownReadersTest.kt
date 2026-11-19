package app.walsoup.serifu.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownReadersTest {
    @Test
    fun browsersAreKnownButNeverAutoSeeded() {
        assertTrue(KnownReaders.packages.contains("com.android.chrome"))
        assertTrue(KnownReaders.packages.contains("org.mozilla.firefox"))
        assertTrue("com.android.chrome" in KnownReaders.browsers)
        assertFalse("com.android.chrome" in KnownReaders.autoSeed)
        assertFalse("org.mozilla.firefox" in KnownReaders.autoSeed)
        assertTrue("app.mihon" in KnownReaders.autoSeed)
    }
}
