package app.serifu.ui.testpage

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.serifu.core.model.SourceLanguage

/**
 * Authentic studio-quality comic pages (Japanese Manga, Korean Manhwa, Chinese Manhua)
 * with authentic manga screentones, artwork, dialogue bubbles, and onomatopoeia.
 */
@Composable
fun SamplePage(
    language: SourceLanguage = SourceLanguage.JA,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(language) {
        val assetName = when (language) {
            SourceLanguage.JA -> "sample/sample_ja.jpg"
            SourceLanguage.KO -> "sample/sample_ko.jpg"
            SourceLanguage.ZH -> "sample/sample_zh.jpg"
        }
        runCatching {
            context.assets.open(assetName).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    if (bitmap != null) {
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        Image(
            bitmap = imageBitmap,
            contentDescription = "${language.englishName} page",
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .aspectRatio(aspect),
            contentScale = ContentScale.FillWidth,
        )
    }
}
