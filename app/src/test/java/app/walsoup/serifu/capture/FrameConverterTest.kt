package app.walsoup.serifu.capture

import android.graphics.Bitmap
import app.serifu.core.geometry.IntRect
import app.serifu.core.image.LumaImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FrameConverterTest {
    @Test
    fun trimMemoryClearsBuffersThreadSafely() {
        val converter = FrameConverter()
        val errors = CopyOnWriteArrayList<Throwable>()

        val threads = List(8) { id ->
            Thread {
                try {
                    val dummyBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                    val dummyLuma = LumaPyramid(
                        LumaImage(16, 16, ByteArray(256)),
                        LumaImage(4, 4, ByteArray(16)),
                        LumaImage(1, 1, ByteArray(1)),
                    )
                    repeat(200) { i ->
                        val frame = CapturedFrame(
                            bitmap = dummyBitmap,
                            luma = dummyLuma,
                            rotation = 0,
                            systemBars = VerticalInsets(0, 0),
                            timestampUptime = 0L,
                            secureBlack = false,
                            onRecycle = converter::recycleBuffers,
                        )
                        if (i % 2 == 0) {
                            converter.recycleBuffers(frame)
                        } else {
                            converter.trimMemory()
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }
        assertEquals(emptyList<Throwable>(), errors)
    }
}
