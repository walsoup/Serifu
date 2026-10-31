package app.serifu.ui.testpage

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.serifu.R
import app.serifu.core.model.SourceLanguage
import app.serifu.ui.design.Serifu

/**
 * One stick-figure page, drawn flat, in Japanese, Korean, and Chinese.
 * A person waters a plastic plant, misses, and offers it a sun umbrella indoors.
 */
@Composable
fun SamplePage(
    language: SourceLanguage = SourceLanguage.JA,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(language) {
        val assetName = when (language) {
            SourceLanguage.JA -> "sample/sample_ja.png"
            SourceLanguage.KO -> "sample/sample_ko.png"
            SourceLanguage.ZH -> "sample/sample_zh.png"
            else -> "sample/sample_ja.png"
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
            contentDescription = stringResource(R.string.sample_page_description, language.englishName),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .aspectRatio(aspect),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        Text(
            text = stringResource(R.string.sample_missing),
            style = Serifu.type.body,
            color = Serifu.colors.textSecondary,
            modifier = modifier.padding(24.dp),
        )
    }
}
