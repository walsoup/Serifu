package app.serifu.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.util.TypedValue
import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.layout.BalloonBreak
import app.serifu.core.layout.BubbleClean
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
            val caps = settings.classicCaps && english
            val styled = style(raw, caps)
            bubbles.add(
                when (cluster.kind) {
                    BubbleKind.SFX -> sfx(cluster, styled, settings, frameW, frameH)
                    BubbleKind.FREE_TEXT -> tag(cluster, styled, settings, frameW, frameH, seamless = false)
                    BubbleKind.DIALOGUE, BubbleKind.CAPTION -> dialogue(cluster, raw, caps, settings, frameW, frameH)
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
        raw: String,
        caps: Boolean,
        settings: Settings,
        frameW: Int,
        frameH: Int,
    ): RenderBubble {
        val paint = letteringPaint(settings, cluster.textColor)
        val display = if (caps) raw.uppercase(Locale.ENGLISH) else raw
        val safe = cluster.letteringRect.toRectF()
        val contours = if (cluster.kind == BubbleKind.DIALOGUE) {
            cluster.bubbleMask?.let { BubbleClean.contours(it, cluster.unionRect) }
        } else {
            null
        }
        val clean = contours?.let { pathOf(it) }
        val organic = clean != null && !clean.isEmpty
        fun laidOut(size: Float, width: Float): StaticLayout {
            paint.textSize = size
            paint.letterSpacing = tracking(display, caps)
            val broken = BalloonBreak.lines(display, width) { paint.measureText(it) }
            val joined = if (broken.isEmpty()) display else broken.joinToString("\n")
            return layout(style(joined, classicCaps = false), paint, width, leading(broken.size))
        }
        // The text never leaves the lettering box. Start at the largest allowed size
        // and step down until the wrapped text fits inside it; no growing past it.
        val box = safe
        val boxWidth = box.width().coerceAtLeast(1f)
        val floor = sp(Tuning.ABSOLUTE_MIN_FONT_SP)
        var size = sp(settings.maxFontSp)
        var fitted = laidOut(size, boxWidth)
        while (!fitsIn(fitted, box) && size > floor) {
            size = (size - 0.5f).coerceAtLeast(floor)
            fitted = laidOut(size, boxWidth)
        }
        val patch = RectF(cluster.patchRect.toRectF())
        patch.union(cluster.unionRect.toRectF())
        if (!organic) patch.union(box)
        val origin = PointF(
            box.left + (box.width() - fitted.width) / 2f,
            box.top + (box.height() - fitted.height) / 2f,
        )
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
            layoutOrigin = origin,
            rotationDeg = 0f,
            seamless = true,
            outlineColor = 0,
            sourceTextForDebug = cluster.sourceText,
            cleanPath = if (organic) clean else null,
        )
    }

    private fun sfx(
        cluster: SpeechBubbleCluster,
        text: CharSequence,
        settings: Settings,
        frameW: Int,
        frameH: Int,
    ): RenderBubble {
        return tag(cluster, text, settings, frameW, frameH, seamless = false)
    }

    private fun tag(
        cluster: SpeechBubbleCluster,
        text: CharSequence,
        settings: Settings,
        frameW: Int,
        frameH: Int,
        seamless: Boolean,
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
        // A tag never leaves the lettering box. It sits on the detected text, and
        // the font shrinks until the wrapped text fits inside that box.
        val area = cluster.letteringRect.toRectF()
        val union = cluster.unionRect.toRectF()
        val padX = dp(Tuning.TAG_PAD_HORIZONTAL_DP)
        val padY = dp(Tuning.TAG_PAD_VERTICAL_DP)
        val maxW = (area.width() - padX * 2f).coerceAtLeast(1f)
        val maxH = (area.height() - padY * 2f).coerceAtLeast(1f)
        val floor = sp(minOf(minSp, Tuning.ABSOLUTE_MIN_FONT_SP))
        var size = sp(maxSp)
        paint.textSize = size
        var fitted = layout(text, paint, maxW)
        while (!(fitted.height <= maxH && !midWordBreak(fitted)) && size > floor) {
            size = (size - 0.5f).coerceAtLeast(floor)
            paint.textSize = size
            fitted = layout(text, paint, maxW)
        }
        // Hug the text: the tag is only as wide as its longest line, so it does not
        // paint over more art than the words need.
        var lineMax = 0f
        for (line in 0 until fitted.lineCount) lineMax = maxOf(lineMax, fitted.getLineWidth(line))
        val tagW = (lineMax + padX * 2f).coerceAtMost(area.width())
        val tagH = (fitted.height + padY * 2f).coerceAtMost(area.height())
        val left = (union.centerX() - tagW / 2f).coerceIn(area.left, (area.right - tagW).coerceAtLeast(area.left))
        val top = (union.centerY() - tagH / 2f).coerceIn(area.top, (area.bottom - tagH).coerceAtLeast(area.top))
        val box = RectF(left, top, left + tagW, top + tagH)
        // The plate has to hide the source ink, which can be larger than the
        // translation and sits a little outside the OCR box once it is rotated.
        val slop = maxOf(dp(4f), cluster.charSizePx * 0.35f)
        val source = RectF(union)
        source.inset(-slop, -slop)
        box.union(source)
        // The layout is maxW wide with centred lines, so centre it on the plate.
        val origin = PointF(
            box.centerX() - fitted.width / 2f,
            box.centerY() - fitted.height / 2f,
        )
        val rotation = cluster.angleDeg.coerceIn(-Tuning.SFX_ANGLE_CLAMP_DEG, Tuning.SFX_ANGLE_CLAMP_DEG)
        val fillAlpha = 255
        return RenderBubble(
            id = cluster.id,
            kind = cluster.kind,
            patch = box,
            patchCornerPx = dp(Tuning.TAG_CORNER_DP),
            fillColor = withAlpha(Tuning.INK_ARGB, fillAlpha),
            textColor = Tuning.PAPER_ARGB,
            layout = fitted,
            layoutOrigin = origin,
            rotationDeg = if (cluster.kind == BubbleKind.SFX) rotation else 0f,
            seamless = seamless,
            outlineColor = 0,
            sourceTextForDebug = cluster.sourceText,
        )
    }

    private fun markOverlaps(bubbles: MutableList<RenderBubble>) {
        val toRemove = mutableSetOf<Int>()
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

                // If two bubbles overlap heavily (> 60%), check for text collisions
                if (overlap > 0.60f * smaller) {
                    val textA = a.layout.text.toString().trim()
                    val textB = b.layout.text.toString().trim()
                    if (textA.equals(textB, ignoreCase = true) || textA.contains(textB)) {
                        toRemove.add(right)
                    } else if (textB.contains(textA)) {
                        toRemove.add(left)
                    } else {
                        // Offset layouts vertically so characters never overlap
                        val topA = a.layoutOrigin.y
                        val bottomA = topA + a.layout.height
                        val topB = b.layoutOrigin.y
                        val bottomB = topB + b.layout.height
                        if (topB < bottomA && bottomB > topA) {
                            val shift = (bottomA - topB) + 4f
                            if (topB + shift + b.layout.height <= b.patch.bottom) {
                                b.layoutOrigin.y += shift
                            } else if (a.layoutOrigin.y - shift >= a.patch.top) {
                                a.layoutOrigin.y -= shift
                            }
                        }
                    }
                }
            }
        }
        if (toRemove.isNotEmpty()) {
            val survivors = bubbles.filterIndexed { index, _ -> index !in toRemove }
            bubbles.clear()
            bubbles.addAll(survivors)
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

    private fun layout(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Float,
        spacing: Float = Tuning.LINE_SPACING_MULTIPLIER,
    ): StaticLayout {
        val width = widthPx.roundToInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, spacing)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    private fun pathOf(contours: List<app.serifu.core.layout.CleanContour>): Path {
        val path = Path()
        for (contour in contours) {
            val xy = contour.xy
            if (xy.size < 6) continue
            path.moveTo(xy[0], xy[1])
            var index = 2
            while (index + 1 < xy.size) {
                path.lineTo(xy[index], xy[index + 1])
                index += 2
            }
            path.close()
        }
        return path
    }

    /** Open tracking on a short shout, a little air on caps, almost none on a paragraph. */
    private fun tracking(text: String, caps: Boolean): Float {
        val letters = text.count { !it.isWhitespace() }
        return when {
            letters in 1..10 -> Tuning.LETTER_SPACING_SHOUT
            caps -> Tuning.LETTER_SPACING_CAPS
            else -> Tuning.LETTER_SPACING_DIALOGUE
        }
    }

    private fun leading(lineCount: Int): Float = when {
        lineCount >= 5 -> Tuning.BALLOON_LEADING_TIGHT
        lineCount >= 3 -> Tuning.BALLOON_LEADING_STACKED
        else -> Tuning.BALLOON_LEADING_OPEN
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

    /** Wrapped text is within [box] vertically and no word is broken mid-way. */
    private fun fitsIn(laid: StaticLayout, box: RectF): Boolean =
        laid.height <= box.height() + 0.5f && !midWordBreak(laid)

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
