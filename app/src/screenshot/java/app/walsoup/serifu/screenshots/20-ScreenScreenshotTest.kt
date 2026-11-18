package app.walsoup.serifu.screenshots

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.serifu.core.Tuning
import app.serifu.core.model.LetteringFont
import app.serifu.core.model.OnDeviceEngine
import app.serifu.core.model.QualityMode
import app.serifu.core.model.SourceLanguage
import app.serifu.core.model.TriggerMode
import app.walsoup.serifu.SerifuApp
import app.walsoup.serifu.debug.PipelineTimings
import app.walsoup.serifu.ml.LanguageModelManager
import app.walsoup.serifu.service.EventRouter
import app.walsoup.serifu.settings.Settings
import app.walsoup.serifu.ui.components.LaunchableApp
import app.walsoup.serifu.ui.components.LocalLaunchableAppsOverride
import app.walsoup.serifu.ui.home.HomeScreen
import app.walsoup.serifu.ui.home.HomeViewModel
import app.walsoup.serifu.ui.onboarding.OnboardingScreen
import app.walsoup.serifu.ui.settings.AboutScreen
import app.walsoup.serifu.ui.settings.BehaviourScreen
import app.walsoup.serifu.ui.settings.DebugScreen
import app.walsoup.serifu.ui.settings.EngineScreen
import app.walsoup.serifu.ui.settings.LanguagesScreen
import app.walsoup.serifu.ui.settings.LetteringScreen
import app.walsoup.serifu.ui.settings.ReadersScreen
import app.walsoup.serifu.ui.testpage.ReaderPage
import app.walsoup.serifu.ui.theme.SerifuTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziActivity
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.registerRoborazziActivityToRobolectricIfNeeded
import java.io.File
import java.net.URL
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the production Compose screens on the JVM. Roborazzi draws them with
 * Robolectric native graphics. The PNGs are gallery recordings, not a baseline
 * comparison.
 *
 * SDK 36, and a new activity per capture: on Robolectric 4.17, SDK 37 PixelCopy
 * keeps the first frame and later captures of that window come back unchanged
 * (https://github.com/robolectric/robolectric/issues/11392). Robolectric's
 * Android 36 jar requires Java 21.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi", application = SerifuApp::class)
class ScreenScreenshotTest {
    @Before
    fun stubOfflineState() {
        blockNetwork()
        val japanese = LanguageModelManager.SOURCE_PACKS.first { it.name == "Japanese" }.code
        val english = LanguageModelManager.POPULAR_TARGET_PACKS.first { it.name == "English" }.code
        LanguageModelManager.probeDownloaded = { setOf(japanese, english) }
        LanguageModelManager.fetchPack = { _, _ -> error("screenshot tests must not download models") }
        val app = ApplicationProvider.getApplicationContext<SerifuApp>()
        // Sideloaded from the shell installer, so the restricted-settings step is the
        // production branch. Robolectric otherwise throws from getInstallSourceInfo.
        shadowOf(app.packageManager).setInstallSourceInfo(
            app.packageName,
            "com.android.shell",
            "com.android.shell",
        )
        val dir = File(app.filesDir, "bergamot")
        dir.mkdirs()
        File(dir, "catalog.json").writeText(FIXTURE_CATALOG)
        app.bergamotModels.refreshInstalled()
    }

    @Test
    fun renderScreens() {
        val outputDir = File(requireProperty("serifu.screenshot.dir"))
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        val expected = File(requireProperty("serifu.screenshot.expected"))
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
        val produced = linkedSetOf<String>()
        val failures = mutableListOf<String>()
        val apps = fixtureApps()
        val screens = shots()
        seed(screens.first().settings)
        val app = ApplicationProvider.getApplicationContext<SerifuApp>()
        val viewModel = HomeViewModel(app.settingsRepository)
        registerRoborazziActivityToRobolectricIfNeeded()
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            for (shot in screens) {
                val name = "${shot.id}-$theme"
                try {
                    seed(shot.settings)
                    val file = File(outputDir, "$name.png")
                    captureScreen(file, shot.awaitDescription) {
                        SerifuTheme(darkTheme = dark) {
                            CompositionLocalProvider(LocalLaunchableAppsOverride provides apps) {
                                shot.Content(viewModel)
                            }
                        }
                    }
                    assertRenderable(file)
                    produced += name
                } catch (t: Throwable) {
                    failures += "$name: ${t::class.simpleName}: ${t.message}"
                    t.printStackTrace()
                }
            }
        }
        val digests = produced.map { name -> sha256(File(outputDir, "$name.png")) }
        if (digests.toSet().size != digests.size) {
            failures += "images are not distinct (${digests.toSet().size} unique of ${digests.size})"
        }
        val missing = expected - produced
        val extra = produced - expected
        if (missing.isNotEmpty() || extra.isNotEmpty() || failures.isNotEmpty()) {
            error(
                buildString {
                    if (failures.isNotEmpty()) {
                        appendLine("Failed renders:")
                        failures.forEach { appendLine("  $it") }
                    }
                    if (missing.isNotEmpty()) appendLine("Missing: $missing")
                    if (extra.isNotEmpty()) appendLine("Unexpected: $extra")
                },
            )
        }
    }

    private class Shot(
        val id: String,
        val settings: Settings,
        val awaitDescription: String? = null,
        val content: @Composable (HomeViewModel) -> Unit,
    ) {
        @Composable
        fun Content(viewModel: HomeViewModel) = content(viewModel)
    }

    private fun shots(): List<Shot> {
        val ready = Settings(
            mode = TriggerMode.AUTO,
            sourceLanguage = SourceLanguage.JA,
            targetLanguage = "en",
            quality = QualityMode.FAST,
            onDeviceEngine = OnDeviceEngine.MLKIT,
            readerPackages = setOf("app.mihon", "org.koitharu.kotatsu"),
            letteringFont = LetteringFont.COMIC_NEUE,
            onboardingDone = true,
            debugOverlay = false,
            wifiOnlyDownloads = false,
        )
        val onboarding = ready.copy(onboardingDone = false, readerPackages = emptySet())
        val tryIt = onboarding.copy(readerPackages = setOf("app.mihon"))
        val debug = ready.copy(debugOverlay = true)
        return listOf(
            Shot("home", ready) { viewModel -> HomeScreen(viewModel) {} },
            Shot("home-scrolled", ready) { viewModel ->
                HomeScreen(viewModel, scrollState = remember { ScrollState(Int.MAX_VALUE) }) {}
            },
            Shot("home-large-font", ready) { viewModel ->
                val base = LocalConfiguration.current
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalConfiguration provides Configuration(base).apply {
                        screenWidthDp = 360
                        fontScale = 1.3f
                    },
                    LocalDensity provides Density(density.density, fontScale = 1.3f),
                ) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        HomeScreen(viewModel) {}
                    }
                }
            },
            Shot("onboarding-1", onboarding) { viewModel ->
                OnboardingScreen(step = 1, viewModel = viewModel, onStep = {}, onBack = {}, onHome = {})
            },
            Shot("onboarding-2", onboarding) { viewModel ->
                OnboardingScreen(step = 2, viewModel = viewModel, onStep = {}, onBack = {}, onHome = {})
            },
            Shot("onboarding-3", onboarding) { viewModel ->
                OnboardingScreen(step = 3, viewModel = viewModel, onStep = {}, onBack = {}, onHome = {})
            },
            Shot("onboarding-4", onboarding) { viewModel ->
                OnboardingScreen(step = 4, viewModel = viewModel, onStep = {}, onBack = {}, onHome = {})
            },
            Shot("onboarding-4-small", onboarding) { viewModel ->
                val base = LocalConfiguration.current
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalConfiguration provides Configuration(base).apply {
                        screenWidthDp = 360
                        fontScale = 1.3f
                    },
                    LocalDensity provides Density(density.density, fontScale = 1.3f),
                ) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        OnboardingScreen(
                            step = 4,
                            viewModel = viewModel,
                            onStep = {},
                            onBack = {},
                            onHome = {},
                            scrollState = remember { ScrollState(Int.MAX_VALUE) },
                        )
                    }
                }
            },
            Shot("onboarding-5", tryIt) { viewModel ->
                OnboardingScreen(step = 5, viewModel = viewModel, onStep = {}, onBack = {}, onHome = {})
            },
            Shot("languages", ready) { viewModel ->
                LanguagesScreen(viewModel = viewModel, onBack = {}, onOpenEngine = {})
            },
            Shot("languages-network", ready) { viewModel ->
                LanguagesScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenEngine = {},
                    scrollState = remember { ScrollState(Int.MAX_VALUE) },
                )
            },
            Shot("readers", ready) { viewModel -> ReadersScreen(viewModel) {} },
            Shot("lettering", ready) { viewModel -> LetteringScreen(viewModel) {} },
            Shot("behaviour", ready) { viewModel -> BehaviourScreen(viewModel) {} },
            Shot("behaviour-delay-long", ready.copy(settleDelayMs = Tuning.SETTLE_DELAY_MAX_MS)) { viewModel ->
                val base = LocalConfiguration.current
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalConfiguration provides Configuration(base).apply {
                        screenWidthDp = 360
                        fontScale = 1.3f
                    },
                    LocalDensity provides Density(density.density, fontScale = 1.3f),
                ) {
                    Box(Modifier.width(360.dp).fillMaxHeight()) {
                        BehaviourScreen(viewModel = viewModel, onBack = {})
                    }
                }
            },
            Shot("engine", ready) { viewModel ->
                EngineScreen(viewModel = viewModel, onBack = {}, onOpenLanguages = {})
            },
            Shot("engine-api", ready.copy(quality = QualityMode.BEST_ONLY)) { viewModel ->
                EngineScreen(viewModel = viewModel, onBack = {}, onOpenLanguages = {})
            },
            Shot("engine-api-custom", ready.copy(quality = QualityMode.BEST_ONLY, aiEndpoint = "https://translate.example.com/v1/chat/completions", aiApiKey = "sk-demo-key")) { viewModel ->
                EngineScreen(viewModel = viewModel, onBack = {}, onOpenLanguages = {})
            },
            Shot("engine-api-gemini", ready.copy(quality = QualityMode.BEST_ONLY, aiEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", aiApiKey = "demo-key")) { viewModel ->
                EngineScreen(viewModel = viewModel, onBack = {}, onOpenLanguages = {})
            },
            Shot("debug", debug) { viewModel -> DebugScreen(viewModel) {} },
            Shot("about", ready) { AboutScreen {} },
            Shot("sample", ready, awaitDescription = "Page 1") { _ ->
                ReaderPage(
                    startBottom = false,
                    requestedLanguage = null,
                    onBack = {},
                    onEnableAccessibility = {},
                )
            },
        )
    }

    private fun captureScreen(
        file: File,
        awaitDescription: String?,
        content: @Composable () -> Unit,
    ) {
        val intent = RoborazziActivity.createIntent(
            ApplicationProvider.getApplicationContext(),
            app.walsoup.serifu.R.style.Theme_Serifu,
        )
        ActivityScenario.launch<RoborazziActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> activity.setContent(content = content) }
            pumpUntil(if (awaitDescription == null) 2_000 else 15_000) {
                awaitDescription == null || hasContentDescription(scenario, awaitDescription)
            }
            scenario.onActivity { activity -> captureComposeRoot(activity, file) }
        }
    }

    private fun pumpUntil(timeoutMs: Long, ready: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val started = System.currentTimeMillis()
        var pumps = 0
        while (System.currentTimeMillis() - started < timeoutMs) {
            looper.idleFor(50, TimeUnit.MILLISECONDS)
            pumps += 1
            // A few frames let Compose finish layout. File and bitmap work runs on
            // real IO threads, so yield the wall clock before deciding the screen is settled.
            if (ready() && pumps >= 3) return
            Thread.sleep(20)
        }
        if (!ready()) error("Timed out waiting for the screen to settle")
    }

    private fun hasContentDescription(scenario: ActivityScenario<out Activity>, text: String): Boolean {
        val found = AtomicBoolean(false)
        scenario.onActivity { activity -> found.set(activityHasDescription(activity, text)) }
        return found.get()
    }

    @SuppressLint("VisibleForTests")
    private fun activityHasDescription(activity: Activity, text: String): Boolean {
        val root = composeRoot(activity) ?: return false
        return root.semanticsOwner.unmergedRootSemanticsNode.containsDescription(text)
    }

    private fun SemanticsNode.containsDescription(text: String): Boolean {
        val descriptions = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        if (descriptions.any { it == text }) return true
        return children.any { it.containsDescription(text) }
    }

    @SuppressLint("VisibleForTests")
    private fun captureComposeRoot(activity: Activity, file: File) {
        val root = composeRoot(activity) ?: error("Compose hierarchy was not attached")
        root.view.captureRoboImage(file)
    }

    @SuppressLint("VisibleForTests")
    private fun composeRoot(activity: Activity): ViewRootForTest? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val composeView = content?.getChildAt(0) as? ComposeView ?: return null
        return composeView.getChildAt(0) as? ViewRootForTest
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun fixtureApps(): List<LaunchableApp> = listOf(
        LaunchableApp("app.mihon", "Mihon", icon(0xFFC8322B.toInt(), "M")),
        LaunchableApp("org.koitharu.kotatsu", "Kotatsu", icon(0xFF2F5D50.toInt(), "K")),
        LaunchableApp("com.android.chrome", "Chrome", icon(0xFF3C4043.toInt(), "C")),
    )

    private fun icon(color: Int, label: String): androidx.compose.ui.graphics.ImageBitmap {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 32f
        }
        canvas.drawText(label, 32f, 42f, paint)
        return bitmap.asImageBitmap()
    }

    private fun seed(settings: Settings) {
        val app = ApplicationProvider.getApplicationContext<SerifuApp>()
        app.isServiceActive.value = true
        app.readerPresence.value = EventRouter.State(
            foregroundPackage = "app.mihon",
            obscured = false,
            reader = true,
        )
        app.pipelineTimings.value = PipelineTimings(
            hideUs = 1_800,
            hideSamples = 4,
            captureMs = 42,
            convertMs = 8,
            fingerprintMs = 3,
            ocrMs = 120,
            clusterMs = 15,
            translateMlKitMs = 80,
            layoutMs = 11,
            totalMs = 279,
            cacheResult = "miss",
        )
        val error = AtomicReference<Throwable>()
        val finished = AtomicBoolean(false)
        app.appScope.launch {
            try {
                withTimeout(10_000) {
                    app.settingsRepository.settings.filterNotNull().first()
                    app.settingsRepository.update { settings }
                    app.settingsRepository.settings.first { current ->
                        current != null &&
                            current.onboardingDone == settings.onboardingDone &&
                            current.sourceLanguage == settings.sourceLanguage &&
                            current.targetLanguage == settings.targetLanguage &&
                            current.readerPackages == settings.readerPackages &&
                            current.mode == settings.mode &&
                            current.debugOverlay == settings.debugOverlay &&
                            current.letteringFont == settings.letteringFont
                    }
                }
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                finished.set(true)
            }
        }
        val started = System.currentTimeMillis()
        while (!finished.get()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() - started > 15_000) {
                error("Timed out writing screenshot settings")
            }
            Thread.sleep(20)
        }
        error.get()?.let { throw it }
    }

    private fun assertRenderable(file: File) {
        check(file.isFile && file.length() >= 4096) {
            "${file.name} is missing or too small (${file.length()} bytes)"
        }
        val header = ByteArray(8)
        file.inputStream().use { stream -> check(stream.read(header) == 8) }
        check(header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte()) {
            "${file.name} is not a PNG"
        }
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        check(bitmap != null && bitmap.width >= 400 && bitmap.height >= 700) {
            "${file.name} decoded as ${bitmap?.width}x${bitmap?.height}"
        }
        val first = bitmap.getPixel(0, 0)
        var varied = false
        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 40).coerceAtLeast(1)
        loop@ for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                if (bitmap.getPixel(x, y) != first) {
                    varied = true
                    break@loop
                }
            }
        }
        bitmap.recycle()
        check(varied) { "${file.name} is a solid color" }
    }

    private fun requireProperty(key: String): String =
        System.getProperty(key) ?: error("Missing system property $key")

    private companion object {
        val FIXTURE_CATALOG = """
            {"data":[
              {"name":"model.jaen.bin","version":"1.0","fileType":"model","architecture":"tiny","sourceLanguage":"ja","targetLanguage":"en","decompressedHash":"m","decompressedSize":1000,"attachment":{"hash":"m","size":800,"filename":"model.zst","location":"fixture/model.zst","mimetype":"application/zstd"}},
              {"name":"lex.jaen.bin","version":"1.0","fileType":"lex","architecture":"tiny","sourceLanguage":"ja","targetLanguage":"en","decompressedHash":"l","decompressedSize":100,"attachment":{"hash":"l","size":80,"filename":"lex.zst","location":"fixture/lex.zst","mimetype":"application/zstd"}},
              {"name":"vocab.jaen.spm","version":"1.0","fileType":"vocab","architecture":"tiny","sourceLanguage":"ja","targetLanguage":"en","decompressedHash":"v","decompressedSize":100,"attachment":{"hash":"v","size":80,"filename":"vocab.zst","location":"fixture/vocab.zst","mimetype":"application/zstd"}}
            ]}
        """.trimIndent()

        fun blockNetwork() {
            if (networkBlocked) return
            runCatching {
                URL.setURLStreamHandlerFactory(URLStreamHandlerFactory { protocol ->
                    if (protocol != "http" && protocol != "https") return@URLStreamHandlerFactory null
                    object : URLStreamHandler() {
                        override fun openConnection(url: URL): java.net.URLConnection {
                            throw java.io.IOException("screenshot tests do not open network connections: $url")
                        }
                    }
                })
            }
            networkBlocked = true
        }

        @Volatile
        var networkBlocked: Boolean = false
    }
}
