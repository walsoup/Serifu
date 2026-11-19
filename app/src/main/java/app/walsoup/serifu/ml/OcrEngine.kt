package app.walsoup.serifu.ml

import android.graphics.Bitmap
import android.graphics.Rect
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.model.SourceLanguage
import app.serifu.core.vision.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.roundToInt

class OcrEngine {
    private val recognizers = mutableMapOf<SourceLanguage, TextRecognizer>()
    private val retiring = mutableListOf<TextRecognizer>()
    private var inFlight = 0
    private val lock = Any()

    suspend fun recognize(bitmap: Bitmap, language: SourceLanguage): List<OcrLine> {
        val longSide = max(bitmap.width, bitmap.height)
        val scaleDown = if (longSide > Tuning.OCR_LONG_SIDE_MAX_PX) {
            Tuning.OCR_LONG_SIDE_MAX_PX.toFloat() / longSide
        } else {
            1f
        }
        // ML Kit tasks are not cancellable: a cancelled coroutine must not free
        // pixels the in-flight task still reads. Always hand it a privately owned
        // copy, recycled only after the task completes.
        val owned = if (scaleDown < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scaleDown).roundToInt().coerceAtLeast(1),
                (bitmap.height * scaleDown).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }
        val back = if (scaleDown < 1f) 1f / scaleDown else 1f
        val recognizer = synchronized(lock) {
            inFlight++
            recognizerLocked(language)
        }
        val task = recognizer.process(InputImage.fromBitmap(owned, 0))
        return try {
            val text = task.await()
            text.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                val box = line.boundingBox?.let { scale(it, back) } ?: return@mapNotNull null
                OcrLine(
                    text = line.text,
                    box = box,
                    confidence = line.confidence,
                    angleDeg = line.angle,
                    language = line.recognizedLanguage,
                    symbolBoxes = line.elements.flatMap { element ->
                        element.symbols.mapNotNull { symbol -> symbol.boundingBox?.let { scale(it, back) } }
                    },
                )
            }
        } finally {
            try {
                if (owned !== bitmap) {
                    if (task.isComplete) {
                        if (!owned.isRecycled) owned.recycle()
                    } else {
                        task.addOnCompleteListener { if (!owned.isRecycled) owned.recycle() }
                    }
                }
            } finally {
                synchronized(lock) {
                    inFlight--
                    if (inFlight == 0 && retiring.isNotEmpty()) {
                        retiring.forEach { it.close() }
                        retiring.clear()
                    }
                }
            }
        }
    }

    fun close() {
        synchronized(lock) {
            recognizers.values.forEach { it.close() }
            recognizers.clear()
            retiring.forEach { it.close() }
            retiring.clear()
        }
    }

    /**
     * Releases native recognizers without replacing this instance.
     * PagePipeline and TranslationCoordinator hold this reference, so the
     * instance must stay stable across memory trims; lazily rebuilt on next use.
     * If an inference is in flight, recognizers are marked for retirement and closed
     * as soon as the in-flight task completes, preventing mid-inference aborts.
     */
    fun reset() {
        synchronized(lock) {
            if (inFlight > 0) {
                retiring.addAll(recognizers.values)
                recognizers.clear()
            } else {
                recognizers.values.forEach { it.close() }
                recognizers.clear()
            }
        }
    }

    private fun recognizerLocked(language: SourceLanguage): TextRecognizer =
        recognizers.getOrPut(language) {
            val options = when (language) {
                SourceLanguage.JA -> JapaneseTextRecognizerOptions.Builder().build()
                SourceLanguage.ZH -> ChineseTextRecognizerOptions.Builder().build()
                SourceLanguage.KO -> KoreanTextRecognizerOptions.Builder().build()
                else -> TextRecognizerOptions.DEFAULT_OPTIONS
            }
            TextRecognition.getClient(options)
        }

    private fun scale(rect: Rect, factor: Float): IntRect {
        if (factor == 1f) return IntRect(rect.left, rect.top, rect.right, rect.bottom)
        return IntRect(
            (rect.left * factor).roundToInt(),
            (rect.top * factor).roundToInt(),
            (rect.right * factor).roundToInt(),
            (rect.bottom * factor).roundToInt(),
        )
    }
}
