package app.serifu.bergamot

/**
 * Thin JNI facade around bergamot-translator. [available] is false when the
 * native library fails to load, so callers can fall back to ML Kit.
 */
object BergamotNative {
    val available: Boolean = runCatching {
        System.loadLibrary("bergamot_jni")
        true
    }.getOrDefault(false)

    external fun nativeLoad(configYaml: String): Long
    external fun nativeTranslate(handle: Long, texts: Array<String>): Array<String>
    external fun nativeTranslatePivot(
        firstHandle: Long,
        secondHandle: Long,
        texts: Array<String>,
    ): Array<String>
    external fun nativeFree(handle: Long)

    fun load(configYaml: String): Long {
        check(available) { "bergamot_jni is not available" }
        return nativeLoad(configYaml)
    }

    fun translate(handle: Long, texts: Array<String>): Array<String> {
        check(available) { "bergamot_jni is not available" }
        return nativeTranslate(handle, texts)
    }

    fun translatePivot(first: Long, second: Long, texts: Array<String>): Array<String> {
        check(available) { "bergamot_jni is not available" }
        return nativeTranslatePivot(first, second, texts)
    }

    fun free(handle: Long) {
        if (!available || handle == 0L) return
        nativeFree(handle)
    }
}
