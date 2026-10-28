package app.serifu.core.translate

import app.serifu.core.Tuning
import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SourceLanguage

enum class EngineId {
    MLKIT,
    GEMMA4_E2B,
}

sealed interface EngineAvailability {
    data object Ready : EngineAvailability
    data class NeedsDownload(val bytes: Long?) : EngineAvailability
    data class Downloading(val bytesDone: Long?, val bytesTotal: Long?) : EngineAvailability
    data class Unsupported(val reason: String) : EngineAvailability
}

data class BubbleText(
    val id: Int,
    val kind: BubbleKind,
    val text: String,
    val charBudget: Int,
)

data class PageTranslationRequest(
    val source: SourceLanguage,
    val target: String,
    val bubbles: List<BubbleText>,
    val previousPageLines: List<String>,
    val keepHonorifics: Boolean,
)

data class PageTranslationResult(
    val engine: EngineId,
    val byId: Map<Int, String>,
)

interface TranslationEngine {
    val id: EngineId
    suspend fun availability(source: SourceLanguage, target: String): EngineAvailability
    suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult
    fun close()
}

object CharBudget {
    fun of(widthPx: Float, heightPx: Float, density: Float): Int {
        val em = Tuning.CHAR_BUDGET_REFERENCE_SP * density
        val glyph = em * em * Tuning.GLYPH_AREA_FACTOR
        if (glyph <= 0f || widthPx <= 0f || heightPx <= 0f) return Tuning.CHAR_BUDGET_MIN
        val count = (widthPx * heightPx / glyph) * Tuning.CHAR_BUDGET_FACTOR
        return count.toInt().coerceAtLeast(Tuning.CHAR_BUDGET_MIN)
    }
}
