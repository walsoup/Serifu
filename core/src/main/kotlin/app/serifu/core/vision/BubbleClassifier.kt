package app.serifu.core.vision

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.image.LumaImage
import app.serifu.core.image.RowSpans
import app.serifu.core.model.BubbleKind
import kotlin.math.sqrt

/**
 * Bubble classification (plan.md 10.8): enclosed dialogue, rectangular
 * caption boxes, sound effects on the art, and free text.
 */
object BubbleClassifier {
    fun classify(
        mask: RowSpans?,
        ringStddev: Float,
        rotated: Boolean,
        charSizePx: Float,
        medianCharSizePx: Float,
    ): BubbleKind {
        if (rotated) return BubbleKind.SFX
        if (medianCharSizePx > 0f && charSizePx > Tuning.SFX_SIZE_FACTOR * medianCharSizePx) {
            return BubbleKind.SFX
        }
        if (ringStddev >= Tuning.SFX_RING_STDDEV_MIN) return BubbleKind.SFX
        if (mask != null && ringStddev < Tuning.DIALOGUE_RING_STDDEV_MAX) {
            return if (mask.fillRatio() > Tuning.CAPTION_FILL_RATIO) BubbleKind.CAPTION else BubbleKind.DIALOGUE
        }
        return BubbleKind.FREE_TEXT
    }

    /** Luma standard deviation on a 3 px ring outside [union], normalised 0–1. */
    fun ringStddev(quarter: LumaImage, unionFrame: IntRect, scale: Int = Tuning.LUMA_PYRAMID_QUARTER): Float {
        val union = IntRect(
            unionFrame.left / scale,
            unionFrame.top / scale,
            (unionFrame.right + scale - 1) / scale,
            (unionFrame.bottom + scale - 1) / scale,
        )
        val ring = Tuning.SAMPLE_RING_PX
        var count = 0
        var mean = 0.0
        // Welford pass 1: mean.
        for (y in union.top - ring until union.bottom + ring) {
            if (y !in 0 until quarter.height) continue
            if (y in union.top until union.bottom) {
                for (x in listOf(union.left - ring until union.left, union.right until union.right + ring)) {
                    for (xx in x) {
                        if (xx !in 0 until quarter.width) continue
                        mean += quarter[xx, y] / 255.0
                        count++
                    }
                }
            } else {
                for (x in union.left - ring until union.right + ring) {
                    if (x !in 0 until quarter.width) continue
                    mean += quarter[x, y] / 255.0
                    count++
                }
            }
        }
        if (count == 0) return 0f
        mean /= count
        var variance = 0.0
        for (y in union.top - ring until union.bottom + ring) {
            if (y !in 0 until quarter.height) continue
            if (y in union.top until union.bottom) {
                for (x in listOf(union.left - ring until union.left, union.right until union.right + ring)) {
                    for (xx in x) {
                        if (xx !in 0 until quarter.width) continue
                        val d = quarter[xx, y] / 255.0 - mean
                        variance += d * d
                    }
                }
            } else {
                for (x in union.left - ring until union.right + ring) {
                    if (x !in 0 until quarter.width) continue
                    val d = quarter[x, y] / 255.0 - mean
                    variance += d * d
                }
            }
        }
        return sqrt(variance / count).toFloat()
    }
}
