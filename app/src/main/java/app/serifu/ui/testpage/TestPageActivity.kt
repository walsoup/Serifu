package app.serifu.ui.testpage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.serifu.overlay.InkPaper
import app.serifu.ui.theme.SerifuTheme

class TestPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startBottom = intent.getBooleanExtra(EXTRA_START_BOTTOM, false)
        setContent {
            SerifuTheme {
                val scroll = rememberScrollState()
                if (startBottom) {
                    LaunchedEffect(Unit) {
                        repeat(20) {
                            kotlinx.coroutines.delay(200)
                            val max = scroll.maxValue
                            if (max > 0) {
                                scroll.scrollTo(max)
                                return@LaunchedEffect
                            }
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .background(Color(InkPaper.PAPER)),
                ) {
                    SamplePage(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f / 2.8f),
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_BOTTOM = "serifu.startBottom"
    }
}
