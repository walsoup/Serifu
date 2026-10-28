package app.serifu.core.vision

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.image.RgbSampler
import app.serifu.core.image.RowSpans
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Bubble paper sampling (plan.md 10.10).
 *
 * Flood-fill success: per-channel median of up to 64 pixels sampled evenly
 * from the filled region. Otherwise: the 75th-luma-percentile sample on a
 * 3 px ring outside the padded union (ignores black glyph ink).
 */
object ColorSampler {
    fun sampleFill(mask: RowSpans, rgb: RgbSampler): Int {
        val rows = ArrayList<Int>()
        for (row in 0 until mask.height) {
            if (mask.minX[row] >= 0) rows.add(row)
        }
        if (rows.isEmpty()) return Tuning.PAPER_ARGB
        val perRow = max(1, Tuning.FILL_SAMPLE_COUNT / rows.size)
        val samples = ArrayList<Int>(Tuning.FILL_SAMPLE_COUNT)
        for (row in rows) {
            val lo = mask.minX[row] * mask.scale
            val hi = (mask.maxX[row] + 1) * mask.scale - 1
            val y = (mask.y0 + row) * mask.scale + mask.scale / 2
            if (y !in 0 until rgb.height) continue
            val width = (hi - lo + 1).coerceAtLeast(1)
            for (i in 0 until perRow) {
                if (samples.size >= Tuning.FILL_SAMPLE_COUNT) break
                val x = (lo + width * (i + 0.5f) / perRow).roundToInt()
                    .coerceIn(0, rgb.width - 1)
                samples.add(rgb.argb(x.coerceIn(lo.coerceAtLeast(0), hi.coerceAtMost(rgb.width - 1)), y))
            }
            if (samples.size >= Tuning.FILL_SAMPLE_COUNT) break
        }
        if (samples.isEmpty()) return Tuning.PAPER_ARGB
        return medianArgb(samples)
    }

    fun sampleRing(unionRect: IntRect, padPx: Int, rgb: RgbSampler): Int {
        val padded = IntRect(
            unionRect.left - padPx, unionRect.top - padPx,
            unionRect.right + padPx, unionRect.bottom + padPx,
        )
        val ring = Tuning.SAMPLE_RING_PX
        val samples = ArrayList<Int>(Tuning.RING_SAMPLE_COUNT * 4)
        // Walk the ring densely, then stride down to 48 samples.
        val top = padded.top - ring
        val bottom = padded.bottom + ring
        val left = padded.left - ring
        val right = padded.right + ring
        for (x in left until right) {
            if (x !in 0 until rgb.width) continue
            if (yIn(top, rgb.height)) samples.add(rgb.argb(x, top))
            if (yIn(bottom - 1, rgb.height)) samples.add(rgb.argb(x, bottom - 1))
        }
        for (y in top until bottom) {
            if (y !in 0 until rgb.height) continue
            if (xIn(left, rgb.width) && y !in padded.top until padded.bottom) samples.add(rgb.argb(left, y))
            if (xIn(right - 1, rgb.width) && y !in padded.top until padded.bottom) {
                samples.add(rgb.argb(right - 1, y))
            }
        }
        // All samples were collected on rows/columns strictly outside the
        // padded rect, so every sample is outside by construction.
        val pool = if (samples.size >= Tuning.RING_SAMPLE_COUNT) {
            val stride = (samples.size + Tuning.RING_SAMPLE_COUNT - 1) / Tuning.RING_SAMPLE_COUNT
            samples.filterIndexed { index, _ -> index % stride == 0 }.take(Tuning.RING_SAMPLE_COUNT)
        } else {
            samples.take(Tuning.RING_SAMPLE_COUNT)
        }
        if (pool.isEmpty()) return Tuning.PAPER_ARGB
        val ranked = pool.sortedBy { luma255(it) }
        return ranked[((ranked.size - 1) * Tuning.RING_LUMA_PERCENTILE).toInt().coerceIn(0, ranked.size - 1)]
    }

    fun textColorFor(fillArgb: Int): Int =
        if (relativeLuminance(fillArgb) > Tuning.TEXT_LUMINANCE_SPLIT) Tuning.INK_ARGB else Tuning.PAPER_ARGB

    fun relativeLuminance(argb: Int): Float {
        fun channel(c: Int): Float {
            val s = c / 255f
            return if (s <= 0.03928f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel((argb shr 16) and 0xFF) +
            0.7152f * channel((argb shr 8) and 0xFF) +
            0.0722f * channel(argb and 0xFF)
    }

    private fun medianArgb(samples: List<Int>): Int {
        fun channel(shift: Int): Int {
            val values = samples.map { (it shr shift) and 0xFF }.sorted()
            val mid = values.size / 2
            return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun luma255(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (Tuning.LUMA_R * r + Tuning.LUMA_G * g + Tuning.LUMA_B * b) shr 8
    }

    private fun xIn(x: Int, w: Int): Boolean = x in 0 until w
    private fun yIn(y: Int, h: Int): Boolean = y in 0 until h
}
