package app.walsoup.serifu.ui.testpage

import android.content.ComponentName
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TestPageActivityExposureTest {
    @Test
    fun samplePageIsNotExported() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, TestPageActivity::class.java),
            0,
        )
        assertFalse(info.exported)
    }

    @Test
    fun samplePageDoesNotWakeOrCoverTheLockScreen() {
        val manifest = manifestFile()
        val xml = manifest.readText()
        val marker = ".ui.testpage.TestPageActivity"
        val nameAt = xml.indexOf(marker)
        assertTrue(nameAt > 0)
        val tag = xml.substring(xml.lastIndexOf("<activity", nameAt), xml.indexOf(">", nameAt))
        assertTrue(tag.contains("android:exported=\"false\""))
        assertFalse(tag.contains("showWhenLocked"))
        assertFalse(tag.contains("turnScreenOn"))

        val source = sourceFile("app/src/main/java/app/walsoup/serifu/ui/testpage/TestPageActivity.kt")
            ?: sourceFile("src/main/java/app/walsoup/serifu/ui/testpage/TestPageActivity.kt")
            ?: error("TestPageActivity.kt not found from ${File(".").absolutePath}")
        val kotlin = source.readText()
        assertFalse(kotlin.contains("setShowWhenLocked"))
        assertFalse(kotlin.contains("setTurnScreenOn"))
    }

    private fun manifestFile(): File =
        sourceFile("app/src/main/AndroidManifest.xml")
            ?: sourceFile("src/main/AndroidManifest.xml")
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")

    private fun sourceFile(path: String): File? = File(path).takeIf { it.isFile }
}
