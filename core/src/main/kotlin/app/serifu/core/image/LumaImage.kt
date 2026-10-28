package app.serifu.core.image

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import kotlin.math.sqrt

class LumaImage(
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
) {
    init {
        require(width > 0 && height > 0 && bytes.size == width * height) {
            "luma ${width}x$height does not match ${bytes.size} bytes"
        }
    }

    operator fun get(x: Int, y: Int): Int = bytes[y * width + x].toInt() and 0xFF
}

object LumaMath {
    fun of(red: Int, green: Int, blue: Int): Int =
        (Tuning.LUMA_R * red + Tuning.LUMA_G * green + Tuning.LUMA_B * blue) shr 8

    fun boxDownsample(
        pixels: IntArray,
        width: Int,
        height: Int,
        factor: Int,
        reuse: ByteArray? = null,
    ): LumaImage {
        require(factor >= 1)
        require(pixels.size >= width * height)
        val outW = (width / factor).coerceAtLeast(1)
        val outH = (height / factor).coerceAtLeast(1)
        val bytes = if (reuse != null && reuse.size == outW * outH) reuse else ByteArray(outW * outH)
        for (oy in 0 until outH) {
            val y0 = oy * factor
            val y1 = minOf(y0 + factor, height)
            for (ox in 0 until outW) {
                val x0 = ox * factor
                val x1 = minOf(x0 + factor, width)
                var sum = 0
                var count = 0
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) {
                        val pixel = pixels[row + x]
                        sum += of((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
                        count++
                    }
                }
                bytes[oy * outW + ox] = (if (count == 0) 0 else sum / count).toByte()
            }
        }
        return LumaImage(outW, outH, bytes)
    }

    fun isSecureBlack(image: LumaImage): Boolean {
        val n = image.bytes.size
        if (n == 0) return false
        var sum = 0L
        for (b in image.bytes) sum += b.toInt() and 0xFF
        val mean = sum.toFloat() / n
        var variance = 0.0
        for (b in image.bytes) {
            val delta = (b.toInt() and 0xFF) - mean
            variance += delta * delta
        }
        val std = sqrt(variance / n).toFloat()
        return mean < Tuning.SECURE_BLACK_MEAN_MAX && std < Tuning.SECURE_BLACK_STDDEV_MAX
    }
}

object PillMask {
    fun sampleRing(pixels: IntArray, width: Int, height: Int, rect: IntRect, ringPx: Int): IntArray {
        if (rect.isEmpty || ringPx <= 0) return IntArray(0)
        val found = ArrayList<Int>()
        val top = rect.top - ringPx
        val bottom = rect.bottom + ringPx
        val left = rect.left - ringPx
        val right = rect.right + ringPx
        for (y in top until bottom) {
            if (y !in 0 until height) continue
            val row = y * width
            val interiorY = y in rect.top until rect.bottom
            for (x in left until right) {
                if (x !in 0 until width) continue
                if (interiorY && x in rect.left until rect.right) continue
                found.add(pixels[row + x])
            }
        }
        return found.toIntArray()
    }

    fun medianArgb(samples: IntArray): Int {
        if (samples.isEmpty()) return 0
        fun channel(shift: Int): Int {
            val values = IntArray(samples.size) { (samples[it] shr shift) and 0xFF }
            values.sort()
            val mid = values.size / 2
            return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun fill(pixels: IntArray, width: Int, rect: IntRect, argb: Int) {
        if (rect.isEmpty) return
        for (y in rect.top until rect.bottom) {
            val row = y * width
            for (x in rect.left until rect.right) {
                pixels[row + x] = argb
            }
        }
    }
}

/**
 * Full-resolution RGB access for colour sampling (plan.md 10.10).
 * Implemented in :app over the screenshot [Bitmap]; kept here so
 * [ColorSampler] stays pure JVM.
 */
interface RgbSampler {
    val width: Int
    val height: Int
    fun argb(x: Int, y: Int): Int
}
