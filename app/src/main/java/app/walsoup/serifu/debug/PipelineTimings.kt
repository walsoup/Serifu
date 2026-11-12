package app.walsoup.serifu.debug

data class PipelineTimings(
    val hideUs: Long = 0,
    val hideSamples: Int = 0,
    val captureMs: Long = 0,
    val convertMs: Long = 0,
    val fingerprintMs: Long = 0,
    val ocrMs: Long = 0,
    val clusterMs: Long = 0,
    val translateMlKitMs: Long = 0,
    val translateBergamotMs: Long = 0,
    val translateGemmaMs: Long = 0,
    val layoutMs: Long = 0,
    val totalMs: Long = 0,
    val cacheResult: String = "",
    val selfDiscarded: Int = 0,
)
