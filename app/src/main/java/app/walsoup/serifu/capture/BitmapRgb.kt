package app.walsoup.serifu.capture

import android.graphics.Bitmap
import app.serifu.core.image.RgbSampler

/** Full-resolution colour access over the screenshot bitmap (plan.md 10.10). */
class BitmapRgb(private val bitmap: Bitmap) : RgbSampler {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override fun argb(x: Int, y: Int): Int = bitmap.getPixel(x, y)
}
