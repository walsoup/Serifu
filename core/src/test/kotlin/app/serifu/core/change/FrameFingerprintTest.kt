package app.serifu.core.change

import app.serifu.core.image.LumaImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameFingerprintTest {
    private fun tiny(fill: (x: Int, y: Int) -> Int): LumaImage {
        val w = 90
        val h = 200
        return LumaImage(w, h, ByteArray(w * h) { i -> fill(i % w, i / w).toByte() })
    }

    @Test
    fun identicalFramesHaveZeroDistance() {
        val image = tiny { x, y -> (x * 7 + y * 13) % 256 }
        val a = FrameFingerprint.of(image, 1440, 3200, 80, 100)
        val b = FrameFingerprint.of(image, 1440, 3200, 80, 100)
        assertEquals(0f, a.distance(b))
        assertTrue(a.isSamePage(b))
    }

    @Test
    fun clockAreaChangeIsMasked() {
        val base = tiny { x, y -> (x * 7 + y * 13) % 256 }
        val clockChanged = tiny { x, y ->
            // Top 3% of the frame changes (status-bar clock); inside the mask.
            if (y < 200 * 3 / 100) (x * 31 + y * 57) % 256 else (x * 7 + y * 13) % 256
        }
        val a = FrameFingerprint.of(base, 1440, 3200, 80, 100)
        val b = FrameFingerprint.of(clockChanged, 1440, 3200, 80, 100)
        assertTrue(a.isSamePage(b), "distance=${a.distance(b)}")
    }

    @Test
    fun contentChangeIsDifferent() {
        val base = tiny { _, _ -> 200 }
        val changed = tiny { _, y -> if (y > 100) 40 else 200 }
        val a = FrameFingerprint.of(base, 1440, 3200, 80, 100)
        val b = FrameFingerprint.of(changed, 1440, 3200, 80, 100)
        assertTrue(!a.isSamePage(b), "distance=${a.distance(b)}")
    }
}
