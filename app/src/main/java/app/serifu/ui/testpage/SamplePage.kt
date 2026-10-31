package app.serifu.ui.testpage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.serifu.R
import app.serifu.core.model.SourceLanguage
import app.serifu.ui.design.Serifu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MangaViewMode {
    BLACK_JACK,
    STICK_SAMPLE,
}

const val BLACK_JACK_PAGE_COUNT = 209

val BLACK_JACK_PAGES: List<String> = (1..BLACK_JACK_PAGE_COUNT).map {
    "manga/blackjack/page_${String.format("%03d", it)}.webp"
}

/**
 * Full vertical scrolling reader for 'Say Hello to Black Jack' (ブラックジャックによろしく)
 * by Shuho Sato (佐藤秀峰), 209 pages, released royalty-free for secondary use.
 */
@Composable
fun MangaReader(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    onPageClick: () -> Unit = {},
) {
    val c = Serifu.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
    ) {
        items(BLACK_JACK_PAGES.size, key = { BLACK_JACK_PAGES[it] }) { index ->
            MangaPageView(
                assetPath = BLACK_JACK_PAGES[index],
                pageNumber = index + 1,
                onClick = onPageClick,
            )
        }
        item(key = "credit_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "『ブラックジャックによろしく』 第1巻",
                    style = Serifu.type.headline.copy(fontSize = 15.sp),
                    color = c.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "著：佐藤秀峰 · 全13巻 二次利用フリー (2012)",
                    style = Serifu.type.caption,
                    color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun MangaPageView(
    assetPath: String,
    pageNumber: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(assetPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(assetPath) {
        withContext(Dispatchers.IO) {
            val loaded = runCatching {
                context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                bitmap = loaded
            }
        }
    }

    DisposableEffect(assetPath) {
        onDispose {
            bitmap = null
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        val aspect = bmp.width.toFloat() / bmp.height.toFloat()
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page $pageNumber",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .aspectRatio(aspect)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7067f)
                .background(Serifu.colors.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        )
    }
}

/**
 * One stick-figure page, drawn flat, in Japanese, Korean, and Chinese.
 * A person waters a plastic plant, misses, and offers it a sun umbrella indoors.
 */
@Composable
fun SamplePage(
    language: SourceLanguage = SourceLanguage.JA,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
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
                .aspectRatio(aspect)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
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
