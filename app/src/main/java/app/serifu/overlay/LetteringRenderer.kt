package app.serifu.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.util.TypedValue
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.layout.MeasuredLine
import app.serifu.core.layout.TextFitSearch
import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SfxMode
import app.serifu.core.vision.OcrLine
import app.serifu.core.vision.SpeechBubbleCluster
import app.serifu.settings.Settings
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds fitted layouts off the main thread. The canvas only draws the result.
 */
class LetteringRenderer(context: Context) {
    private val appContext = context.applicationContext

    fun build(
        clusters: List<SpeechBubbleCluster>,
        translations: Map<Int, String>,
        settings: Settings,
        frameW: Int,
        frameH: Int,
        rotation: Int,
        debugLines: List<OcrLine>,
        debugClusters: List<SpeechBubbleCluster> = clusters,
        debugWalls: List<Pair<IntRect, IntRect>> = emptyList(),
        debugDropped: List<OcrLine> = emptyList(),
    ): RenderPage {
        val english = settings.targetLanguage.equals("en", ignoreCase = true)
        val bubbles = ArrayList<RenderBubble>()
        for (cluster in clusters) {
            val raw = translations[cluster.id]?.trim().orEmpty()
            if (raw.isEmpty()) continue
            if (cluster.kind == BubbleKind.SFX && settings.sfxMode == SfxMode.OFF) continue
            val styled = style(raw, settings.classicCaps && english)
            bubbles.add(
                when (cluster.kind) {
                    BubbleKind.SFX -> sfx(cluster, styled, settings, frameW, frameH)
                    BubbleKind.FREE_TEXT -> tag(cluster, styled, settings, frameW, frameH, seamless = false)
                    BubbleKind.DIALOGUE, BubbleKind.CAPTION -> dialogue(cluster, styled, settings, frameW, frameH)
                },
            )
        }
        markOverlaps(bubbles)
        return RenderPage(
            bubbles = bubbles,
            frameW = frameW,
            frameH = frameH,
            rotation = rotation,
            sourceLines = clusters.map { it.sourceText },
            debugLines = debugLines,
            debugClusters = debugClusters,
            debugWalls = debugWalls,
            debugDropped = debugDropped,
        )
    }

    private fun dialogue(
        cluster: SpeechBubbleCluster,
        text: CharSequence,
        settings: Settings,
        frameW: Int,
        frameH: Int,
    ): RenderBubble {
        val paint = letteringPaint(settings, cluster.textColor)
        val start = cluster.letteringRect.toRectF()
        val fit = TextFitSearch.fit(
            boxWidth = start.width().coerceAtLeast(1f),
            boxHeight = start.height().coerceAtLeast(1f),
            minPx = sp(settings.minFontSp),
            maxPx = sp(settings.maxFontSp),
            absoluteMinPx = sp(Tuning.ABSOLUTE_MIN_FONT_SP),
        ) { size, width ->
            paint.textSize = size
            val probe = layout(text, paint, width)
            MeasuredLine(probe.height.toFloat(), midWordBreak(probe))
        }
        paint.textSize = fit.sizePx
        val fitted = layout(text, paint, fit.widthPx)
        val box = place(start.centerX(), start.centerY(), fit.widthPx, fit.heightPx, frameW, frameH)
        val patch = RectF(cluster.patchRect.toRectF())
        patch.union(cluster.unionRect.toRectF())
        patch.union(box)
        val corner = dp(
            if (cluster.kind == BubbleKind.CAPTION) Tuning.CAPTION_CORNER_DP else Tuning.DIALOGUE_CORNER_DP,
        )
        return RenderBubble(
            id = cluster.id,
            kind = cluster.kind,
            patch = patch,
            patchCornerPx = corner,
            fillColor = withAlpha(cluster.fillColor, Tuning.FILL_ALPHA),
            textColor = cluster.textColor,
            layout = fitted,
            layoutOrigin = PointF(
                box.left + (box.width() - fitted.width) / 2f,
                box.top + (box.height() - fitted.height) / 2f,
            ),
            rotationDeg = 0f,
            seamless = true,
            outlineColor = 0,
            sourceTextForDebug = cluster.sourceText,
        )
    }

    private fun sfx(
        cluster: SpeechBubbleCluster,
        text: CharSequence,
        settings: Settings,
        frameW: Int,
        frameH: Int,
    ): RenderBubble {
        val cover = settings.sfxMode == SfxMode.COVER
        return tag(cluster, text, settings, frameW, frameH, seamless = false, cover = cover)
    }

    private fun tag(
        cluster: SpeechBubbleCluster,
        text: CharSequence,
        settings: Settings,
        frameW: Int,
        frameH: Int,
        seamless: Boolean,
        cover: Boolean = false,
    ): RenderBubble {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            isSubpixelText = true
            color = Tuning.PAPER_ARGB
            typeface = if (cluster.kind == BubbleKind.SFX) {
                OverlayTypefaces.sfx(appContext)
            } else {
                OverlayTypefaces.of(appContext, settings.letteringFont)
            }
        }
        val minSp = if (cluster.kind == BubbleKind.SFX) Tuning.SFX_MIN_SP else settings.minFontSp
        val maxSp = if (cluster.kind == BubbleKind.SFX) Tuning.SFX_MAX_SP else settings.maxFontSp
        val limit = if (cover) cluster.patchRect.toRectF() else RectF(0f, 0f, frameW * 0.46f, sp(maxSp) * 2.4f)
        val fit = TextFitSearch.fit(
            boxWidth = limit.width().coerceAtLeast(1f),
            boxHeight = limit.height().coerceAtLeast(1f),
            minPx = sp(minSp),
            maxPx = sp(maxSp),
            absoluteMinPx = sp(minOf(minSp, Tuning.ABSOLUTE_MIN_FONT_SP)),
        ) { size, width ->
            paint.textSize = size
            val probe = layout(text, paint, width)
            MeasuredLine(probe.height.toFloat(), midWordBreak(probe))
        }
        paint.textSize = fit.sizePx
        val fitted = layout(text, paint, fit.widthPx)
        val padX = dp(Tuning.TAG_PAD_HORIZONTAL_DP)
        val padY = dp(Tuning.TAG_PAD_VERTICAL_DP)
        val tagW = fitted.width + padX * 2f
        val tagH = fitted.height + padY * 2f
        val patch = if (cover) {
            val box = place(
                cluster.patchRect.centerX.toFloat(),
                cluster.patchRect.centerY.toFloat(),
                maxOf(fit.widthPx, cluster.patchRect.width.toFloat()),
                maxOf(fit.heightPx, cluster.patchRect.height.toFloat()),
                frameW,
                frameH,
            )
            box.union(cluster.unionRect.toRectF())
            box
        } else {
            val anchor = cluster.unionRect
            var left = anchor.right + dp(Tuning.PATCH_PAD_DP)
            if (left + tagW > frameW) left = anchor.left - tagW - dp(Tuning.PATCH_PAD_DP)
            val top = anchor.centerY - tagH / 2f
            place(left + tagW / 2f, top + tagH / 2f, tagW, tagH, frameW, frameH)
        }
        val rotation = cluster.angleDeg.coerceIn(-Tuning.SFX_ANGLE_CLAMP_DEG, Tuning.SFX_ANGLE_CLAMP_DEG)
        return RenderBubble(
            id = cluster.id,
            kind = cluster.kind,
            patch = patch,
            patchCornerPx = dp(Tuning.TAG_CORNER_DP),
            fillColor = withAlpha(Tuning.INK_ARGB, (Tuning.TAG_ALPHA * 255f).roundToInt()),
            textColor = Tuning.PAPER_ARGB,
            layout = fitted,
            layoutOrigin = PointF(patch.left + padX, patch.top + padY),
            rotationDeg = if (cluster.kind == BubbleKind.SFX) rotation else 0f,
            seamless = seamless,
            outlineColor = 0,
            sourceTextForDebug = cluster.sourceText,
        )
    }

    private fun markOverlaps(bubbles: List<RenderBubble>) {
        for (left in bubbles.indices) {
            for (right in left + 1 until bubbles.size) {
                val a = bubbles[left]
                val b = bubbles[right]
                if (a.patch.isEmpty || b.patch.isEmpty) continue
                val overlap = overlapArea(a.patch, b.patch)
                val smaller = minOf(a.patch.width() * a.patch.height(), b.patch.width() * b.patch.height())
                if (smaller <= 0f || overlap <= Tuning.OVERLAP_OUTLINE_FRACTION * smaller) continue
                a.outlineColor = outlineFor(a.fillColor)
                b.outlineColor = outlineFor(b.fillColor)
            }
        }
    }

    private fun letteringPaint(settings: Settings, color: Int): TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        this.color = color
        typeface = OverlayTypefaces.of(appContext, settings.letteringFont)
        letterSpacing = if (settings.classicCaps && settings.targetLanguage.equals("en", ignoreCase = true)) {
            Tuning.LETTER_SPACING_CAPS
        } else {
            0f
        }
    }

    private fun style(raw: String, classicCaps: Boolean): CharSequence {
        val text = if (classicCaps) raw.uppercase(Locale.ENGLISH) else raw
        if (!text.contains('*')) return text
        val builder = SpannableStringBuilder()
        var index = 0
        while (index < text.length) {
            val open = text.indexOf('*', index)
            if (open < 0) {
                builder.append(text.substring(index))
                break
            }
            val close = text.indexOf('*', open + 1)
            if (close < 0) {
                builder.append(text.substring(index))
                break
            }
            builder.append(text.substring(index, open))
            val start = builder.length
            builder.append(text.substring(open + 1, close))
            builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = close + 1
        }
        return builder
    }

    private fun layout(text: CharSequence, paint: TextPaint, widthPx: Float): StaticLayout {
        val width = widthPx.roundToInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, Tuning.LINE_SPACING_MULTIPLIER)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(
                if (Build.VERSION.SDK_INT >= 33) {
                    Layout.HYPHENATION_FREQUENCY_NORMAL_FAST
                } else {
                    Layout.HYPHENATION_FREQUENCY_NORMAL
                },
            )
            .build()
    }

    private fun midWordBreak(fitted: StaticLayout): Boolean {
        val text = fitted.text
        if (fitted.lineCount <= 1) return false
        for (line in 0 until fitted.lineCount - 1) {
            val end = fitted.getLineEnd(line)
            if (end <= 0 || end >= text.length) continue
            val before = text[end - 1]
            val after = text[end]
            if (before == '-' || before == '\n') continue
            if (before.isLetter() && after.isLetter()) return true
        }
        return false
    }

    private fun place(centerX: Float, centerY: Float, width: Float, height: Float, frameW: Int, frameH: Int): RectF {
        var left = centerX - width / 2f
        var top = centerY - height / 2f
        if (width <= frameW) left = left.coerceIn(0f, frameW - width)
        if (height <= frameH) top = top.coerceIn(0f, frameH - height)
        return RectF(left, top, left + width, top + height)
    }

    private fun overlapArea(a: RectF, b: RectF): Float {
        val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (width <= 0f || height <= 0f) return 0f
        return width * height
    }

    private fun outlineFor(fill: Int): Int {
        val toward = if (Color.luminance(fill) > Tuning.TEXT_LUMINANCE_SPLIT) Color.BLACK else Color.WHITE
        return blend(fill, toward, Tuning.OVERLAP_OUTLINE_CONTRAST)
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val start = (from shr shift) and 0xFF
            val end = (to shr shift) and 0xFF
            return (start + (end - start) * amount).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, appContext.resources.displayMetrics)

    private fun sp(value: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), appContext.resources.displayMetrics)

    private fun IntRect.toRectF(): RectF = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}
