package app.walsoup.serifu.ml

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OcrEngineTest {
    @Test
    fun resetIsCallableSafelyWhenEmptyOrPopulated() {
        val ocr = OcrEngine()
        // Resetting an unused engine is safe.
        ocr.reset()
        ocr.close()
        assertNotNull(ocr)
    }
}
