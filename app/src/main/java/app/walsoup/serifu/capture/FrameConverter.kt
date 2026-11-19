package app.walsoup.serifu.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.image.LumaImage
import app.serifu.core.image.LumaMath
import app.serifu.core.image.PillMask

data class VerticalInsets(val top: Int, val bottom: Int)

class LumaPyramid(
    val full: LumaImage,
    val quarter: LumaImage,
    val tiny: LumaImage,
)

class CapturedFrame(
    val bitmap: Bitmap,
    val luma: LumaPyramid,
    val rotation: Int,
    val systemBars: VerticalInsets,
    val timestampUptime: Long,
    val secureBlack: Boolean,
    var captureMs: Long = 0L,
    var convertMs: Long = 0L,
    private val onRecycle: (CapturedFrame) -> Unit,
) {
    private var recycled = false

    fun recycle() {
        if (recycled) return
        recycled = true
        if (!bitmap.isRecycled) bitmap.recycle()
        onRecycle(this)
    }
}

/**
 * Turns a hardware screenshot into a software bitmap, masks the pill, and builds the luma pyramid.
 * Byte arrays are reused only after the frame that owned them has been recycled, so two live frames never alias.
 */
class FrameConverter {
    private val poolLock = Any()
    private var pixelBuf = IntArray(0)
    private val spareFull = ArrayDeque<ByteArray>()
    private val spareQuarter = ArrayDeque<ByteArray>()
    private val spareTiny = ArrayDeque<ByteArray>()

    fun convert(
        hardwareBuffer: HardwareBuffer,
        colorSpace: ColorSpace?,
        timestampUptime: Long,
        rotation: Int,
        pillRect: IntRect?,
        systemBars: VerticalInsets,
    ): CapturedFrame {
        val hardware = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
            ?: error("wrapHardwareBuffer returned null")
        val software = try {
            hardware.copy(Bitmap.Config.ARGB_8888, true) ?: drawSoftwareCopy(hardware)
        } finally {
            hardware.recycle()
        }
        val width = software.width
        val height = software.height
        val pixels = synchronized(poolLock) {
            if (pixelBuf.size < width * height) pixelBuf = IntArray(width * height)
            pixelBuf
        }
        software.getPixels(pixels, 0, width, 0, 0, width, height)
        val frame = IntRect(0, 0, width, height)
        val pill = pillRect?.intersect(frame)
        if (pill != null && !pill.isEmpty) {
            val samples = PillMask.sampleRing(pixels, width, height, pill, Tuning.PILL_MASK_RING_PX)
            val fill = PillMask.medianArgb(samples)
            if (samples.isNotEmpty()) {
                PillMask.fill(pixels, width, pill, fill)
                software.setPixels(pixels, pill.top * width + pill.left, width, pill.left, pill.top, pill.width, pill.height)
            }
        }
        val quarter = LumaMath.boxDownsample(pixels, width, height, Tuning.LUMA_PYRAMID_QUARTER, rent(spareQuarter, width / 4, height / 4))
        val tiny = LumaMath.boxDownsample(
            pixels,
            width,
            height,
            Tuning.LUMA_PYRAMID_TINY,
            rent(spareTiny, width / Tuning.LUMA_PYRAMID_TINY, height / Tuning.LUMA_PYRAMID_TINY),
        )
        val full = LumaMath.boxDownsample(pixels, width, height, 1, rent(spareFull, width, height))
        return CapturedFrame(
            bitmap = software,
            luma = LumaPyramid(full, quarter, tiny),
            rotation = rotation,
            systemBars = systemBars,
            timestampUptime = timestampUptime,
            secureBlack = LumaMath.isSecureBlack(quarter),
            onRecycle = ::recycleBuffers,
        )
    }

    private fun rent(pool: ArrayDeque<ByteArray>, width: Int, height: Int): ByteArray? = synchronized(poolLock) {
        val size = width.coerceAtLeast(1) * height.coerceAtLeast(1)
        val spare = pool.removeFirstOrNull() ?: return@synchronized null
        return if (spare.size == size) spare else null
    }

    internal fun recycleBuffers(frame: CapturedFrame) = synchronized(poolLock) {
        keep(spareFull, frame.luma.full.bytes)
        keep(spareQuarter, frame.luma.quarter.bytes)
        keep(spareTiny, frame.luma.tiny.bytes)
    }

    private fun keep(pool: ArrayDeque<ByteArray>, bytes: ByteArray) {
        if (pool.none { it === bytes }) pool.addLast(bytes)
        while (pool.size > 1) pool.removeFirst()
    }

    private fun drawSoftwareCopy(hardware: Bitmap): Bitmap {
        val copy = Bitmap.createBitmap(hardware.width, hardware.height, Bitmap.Config.ARGB_8888)
        Canvas(copy).drawBitmap(hardware, 0f, 0f, null)
        // The pill-mask pass calls setPixels: never hand it an immutable bitmap.
        return if (copy.isMutable) copy else copy.copy(Bitmap.Config.ARGB_8888, true) ?: copy
    }

    /** Releases pooled buffers; next capture reallocates. Thread-safe across Main and capture threads. */
    fun trimMemory() = synchronized(poolLock) {
        pixelBuf = IntArray(0)
        spareFull.clear()
        spareQuarter.clear()
        spareTiny.clear()
    }
}
