package app.serifu.core.change

import app.serifu.core.Tuning
import app.serifu.core.model.SourceLanguage
import app.serifu.core.translate.EngineId

/**
 * LRU of recent pages keyed by fingerprint (plan.md 9.2). Flipping back a
 * page is instant; a hit with a different language or engine is a miss.
 * Cleared on rotation and reader change by the caller.
 */
class PageCache<T>(private val maxSize: Int = Tuning.PAGE_CACHE_SIZE) {
    data class Entry<T>(
        val fingerprint: FrameFingerprint,
        val rotation: Int,
        val source: SourceLanguage,
        val target: String,
        val engine: EngineId,
        val variant: String?,
        val value: T,
    )

    // Thread-safe: hit from Dispatchers.Default, IO/Main upgrade jobs, and the event loop.
    private val entries = ArrayDeque<Entry<T>>()

    fun get(
        fingerprint: FrameFingerprint,
        rotation: Int,
        source: SourceLanguage,
        target: String,
        engine: EngineId,
        variant: String? = null,
    ): T? = synchronized(entries) {
        val index = entries.indexOfFirst { entry ->
            entry.rotation == rotation &&
                entry.source == source &&
                entry.target == target &&
                entry.engine == engine &&
                entry.variant == variant &&
                entry.fingerprint.frameWidth == fingerprint.frameWidth &&
                entry.fingerprint.frameHeight == fingerprint.frameHeight &&
                entry.fingerprint.isSamePage(fingerprint)
        }
        if (index < 0) return@synchronized null
        val hit = entries.removeAt(index)
        entries.addFirst(hit)
        hit.value
    }

    fun put(
        fingerprint: FrameFingerprint,
        rotation: Int,
        source: SourceLanguage,
        target: String,
        engine: EngineId,
        value: T,
    ) {
        put(fingerprint, rotation, source, target, engine, null, value)
    }

    fun put(
        fingerprint: FrameFingerprint,
        rotation: Int,
        source: SourceLanguage,
        target: String,
        engine: EngineId,
        variant: String?,
        value: T,
    ) = synchronized(entries) {
        entries.removeAll {
            it.rotation == rotation && it.source == source && it.target == target &&
                it.engine == engine && it.variant == variant &&
                it.fingerprint.frameWidth == fingerprint.frameWidth &&
                it.fingerprint.frameHeight == fingerprint.frameHeight &&
                it.fingerprint.isSamePage(fingerprint)
        }
        entries.addFirst(Entry(fingerprint, rotation, source, target, engine, variant, value))
        while (entries.size > maxSize) entries.removeLast()
    }

    fun clear() = synchronized(entries) { entries.clear() }

    fun size(): Int = synchronized(entries) { entries.size }
}
