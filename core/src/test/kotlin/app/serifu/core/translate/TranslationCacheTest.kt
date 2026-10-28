package app.serifu.core.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranslationCacheTest {
    @Test
    fun keyAndLeastRecentlyUsedEviction() {
        val cache = TranslationCache(maxEntries = 2)
        val first = cache.key(EngineId.MLKIT, "en", "ja|おはよう")
        val second = cache.key(EngineId.MLKIT, "en", "ja|約束")
        val third = cache.key(EngineId.MLKIT, "en", "ja|行こう")
        cache.put(first, "Good morning")
        cache.put(second, "It's a promise")
        assertEquals("Good morning", cache.get(first))
        cache.put(third, "Let's go")
        assertNull(cache.get(second))
        assertEquals("Good morning", cache.get(first))
        val blank = cache.key(EngineId.MLKIT, "en", "ja|blank")
        cache.put(blank, "   ")
        assertNull(cache.get(blank))
    }
}
