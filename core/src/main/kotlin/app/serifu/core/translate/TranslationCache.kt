package app.serifu.core.translate

import app.serifu.core.Tuning

class TranslationCache(
    private val maxEntries: Int = Tuning.TRANSLATION_CACHE_SIZE,
) {
    private val entries = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > maxEntries
    }

    fun key(engine: EngineId, target: String, cacheKey: String): String = "$engine|$target|$cacheKey"

    fun get(key: String): String? = synchronized(entries) { entries[key] }

    fun put(key: String, value: String) {
        if (value.isBlank()) return
        synchronized(entries) { entries[key] = value }
    }
}
