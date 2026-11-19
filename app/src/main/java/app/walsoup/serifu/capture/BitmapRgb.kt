package app.walsoup.serifu.capture

import android.graphics.Bitmap
import app.serifu.core.image.RgbSampler

/**
 * Full-resolution colour access over the screenshot bitmap (plan.md 10.10).
 * One JNI call per pixel sample is too slow for ring walks, so the current
 * row is bulk-fetched once; both samplers walk row-major.
 */
class BitmapRgb(private val bitmap: Bitmap) : RgbSampler {
    private var cachedRow = -1
    private var rowBuf = IntArray(0)

    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override fun argb(x: Int, y: Int): Int {
        if (y != cachedRow || rowBuf.size < bitmap.width) {
            if (rowBuf.size < bitmap.width) rowBuf = IntArray(bitmap.width)
            bitmap.getPixels(rowBuf, 0, bitmap.width, 0, y, bitmap.width, 1)
            cachedRow = y
        }
        return rowBuf[x]
    }
}
