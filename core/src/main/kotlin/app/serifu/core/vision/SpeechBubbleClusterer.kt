package app.serifu.core.vision

import app.serifu.core.Tuning
import app.serifu.core.geometry.IntRect
import app.serifu.core.geometry.RectMath
import app.serifu.core.image.LumaImage
import app.serifu.core.image.RgbSampler
import app.serifu.core.model.BubbleKind
import app.serifu.core.model.SourceLanguage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ClusterConfig(
    val source: SourceLanguage,
    val frameWidth: Int,
    val frameHeight: Int,
    val patchPadPx: Int,
    val skipLatin: Boolean,
    val pill: IntRect?,
)

/** Image inputs for the vision-quality path (plan.md 10.5, 10.9, 10.10). */
data class VisionInputs(
    val full: LumaImage,
    val quarter: LumaImage,
    val rgb: RgbSampler,
    val refUnit: Float,
)

data class ClusterOutcome(
    val clusters: List<SpeechBubbleCluster>,
    val inkWalls: List<Pair<IntRect, IntRect>>,
    val droppedFurigana: List<OcrLine>,
)

/**
 * Groups OCR lines into bubbles. The basic path uses orientation, size and
 * proximity (C1–C3); [clusterWithVision] adds furigana removal, the ink-wall
 * test (C4), region estimation, colour sampling and classification.
 */
object SpeechBubbleClusterer {
    fun cluster(lines: List<OcrLine>, config: ClusterConfig): List<SpeechBubbleCluster> {
        val kept = LineFilter.keep(
            lines,
            config.frameWidth,
            config.frameHeight,
            config.skipLatin,
            config.pill,
        )
        if (kept.isEmpty()) return emptyList()
        val furigana = FuriganaFilter.filter(kept)
        val unionFind = UnionFind(furigana.kept.size)
        for (left in furigana.kept.indices) {
            for (right in left + 1 until furigana.kept.size) {
                if (merges(furigana.kept[left], furigana.kept[right])) unionFind.union(left, right)
            }
        }
        val frame = IntRect(0, 0, config.frameWidth, config.frameHeight)
        val built = unionFind.groups().mapNotNull { indexes ->
            build(indexes.map { furigana.kept[it] }, config, frame)
        }
        return ReadingOrder.sort(built, config.source, config.frameHeight)
            .mapIndexed { index, cluster -> cluster.copy(id = index) }
    }

    fun clusterWithVision(
        lines: List<OcrLine>,
        config: ClusterConfig,
        vision: VisionInputs,
    ): ClusterOutcome {
        val kept = LineFilter.keep(
            lines,
            config.frameWidth,
            config.frameHeight,
            config.skipLatin,
            config.pill,
        )
        if (kept.isEmpty()) return ClusterOutcome(emptyList(), emptyList(), emptyList())
        val furigana = FuriganaFilter.filter(kept)
        val members = furigana.kept
        val unionFind = UnionFind(members.size)
        val walls = ArrayList<Pair<IntRect, IntRect>>()
        for (left in members.indices) {
            for (right in left + 1 until members.size) {
                val a = members[left]
                val b = members[right]
                if (!mergesBasic(a, b)) continue
                val orientation = pairOrientation(a, b)
                if (InkWallDetector.hasWall(a.line.box, b.line.box, orientation, vision.full, vision.refUnit)) {
                    walls.add(a.line.box to b.line.box)
                    continue
                }
                unionFind.union(left, right)
            }
        }
        val frame = IntRect(0, 0, config.frameWidth, config.frameHeight)
        val prelim = unionFind.groups().mapNotNull { indexes ->
            prelimBuild(indexes.map { members[it] }, config, frame)
        }
        if (prelim.isEmpty()) {
            return ClusterOutcome(emptyList(), walls, furigana.dropped.map { it.line })
        }
        val medianChar = prelim.map { it.charSizePx }.sorted().let { it[it.size / 2] }
        val enriched = prelim.map { part ->
            enrich(part, config, vision, medianChar)
        }
        val sorted = ReadingOrder.sort(enriched, config.source, config.frameHeight)
            .mapIndexed { index, cluster -> cluster.copy(id = index) }
        return ClusterOutcome(sorted, walls, furigana.dropped.map { it.line })
    }

    private data class Prelim(
        val orientation: Orientation,
        val unionRect: IntRect,
        val sourceText: String,
        val cacheKey: String,
        val charSizePx: Float,
        val angleDeg: Float,
        val rotated: Boolean,
        val members: List<OcrLine>,
    )

    private fun prelimBuild(members: List<FilteredLine>, config: ClusterConfig, frame: IntRect): Prelim? {
        if (members.isEmpty()) return null
        val lines = members.map { it.line }
        val orientation = ReadingOrder.majority(members.map { it.orientation }, config.source)
        val sourceText = ReadingOrder.assemble(lines, members.map { it.orientation }, config.source)
        if (sourceText.isEmpty()) return null
        var union = IntRect.Empty
        for (line in lines) union = union.union(line.box)
        val sizes = members.map { it.charSize }.sorted()
        val anchor = members.maxBy { it.charSize }
        return Prelim(
            orientation = orientation,
            unionRect = union,
            sourceText = sourceText,
            cacheKey = TextNormalizer.cacheKey(config.source, sourceText),
            charSizePx = sizes[sizes.size / 2],
            angleDeg = anchor.line.angleDeg,
            rotated = members.all { it.rotated },
            members = lines,
        )
    }

    private fun enrich(
        part: Prelim,
        config: ClusterConfig,
        vision: VisionInputs,
        medianChar: Float,
    ): SpeechBubbleCluster {
        val region = BubbleRegionEstimator.estimate(
            quarter = vision.quarter,
            unionRect = part.unionRect,
            charSizePx = part.charSizePx,
            padPx = config.patchPadPx,
        )
        val ringStd = BubbleClassifier.ringStddev(vision.quarter, part.unionRect)
        val kind = BubbleClassifier.classify(
            mask = region.mask,
            ringStddev = ringStd,
            rotated = part.rotated,
            charSizePx = part.charSizePx,
            medianCharSizePx = medianChar,
            orientation = part.orientation,
        )
        val fill = if (region.mask != null) {
            ColorSampler.sampleFill(region.mask, vision.rgb)
        } else {
            ColorSampler.sampleRing(part.unionRect, config.patchPadPx, vision.rgb)
        }
        return SpeechBubbleCluster(
            id = 0,
            kind = kind,
            sourceText = part.sourceText,
            cacheKey = part.cacheKey,
            orientation = part.orientation,
            unionRect = part.unionRect,
            patchRect = region.patchRect,
            letteringRect = region.letteringRect,
            bubbleMask = region.mask,
            fillColor = fill,
            textColor = ColorSampler.textColorFor(fill),
            charSizePx = part.charSizePx,
            angleDeg = part.angleDeg,
            members = part.members,
        )
    }

    private fun build(members: List<FilteredLine>, config: ClusterConfig, frame: IntRect): SpeechBubbleCluster? {
        if (members.isEmpty()) return null
        val lines = members.map { it.line }
        val orientation = ReadingOrder.majority(members.map { it.orientation }, config.source)
        val sourceText = ReadingOrder.assemble(lines, members.map { it.orientation }, config.source)
        if (sourceText.isEmpty()) return null
        var union = IntRect.Empty
        for (line in lines) union = union.union(line.box)
        val patch = union.expand(config.patchPadPx).intersect(frame).let { clipped ->
            if (clipped.isEmpty) union else clipped
        }
        val sizes = members.map { it.charSize }.sorted()
        val anchor = members.maxBy { it.charSize }
        val kind = if (members.all { it.rotated }) BubbleKind.SFX else BubbleKind.DIALOGUE
        return SpeechBubbleCluster(
            id = 0,
            kind = kind,
            sourceText = sourceText,
            cacheKey = TextNormalizer.cacheKey(config.source, sourceText),
            orientation = orientation,
            unionRect = union,
            patchRect = patch,
            letteringRect = patch,
            fillColor = Tuning.PAPER_ARGB,
            textColor = Tuning.INK_ARGB,
            charSizePx = sizes[sizes.size / 2],
            angleDeg = anchor.line.angleDeg,
            members = lines,
        )
    }

    private fun merges(left: FilteredLine, right: FilteredLine): Boolean {
        if (!mergesBasic(left, right)) return false
        return true
    }

    private fun mergesBasic(left: FilteredLine, right: FilteredLine): Boolean {
        if (!compatible(left.orientation, right.orientation)) return false
        val small = min(left.charSize, right.charSize)
        val large = max(left.charSize, right.charSize)
        if (small <= 0f || large / small > Tuning.SIZE_COMPAT_RATIO) return false
        return closeEnough(left, right, large)
    }

    private fun pairOrientation(left: FilteredLine, right: FilteredLine): Orientation = when {
        left.orientation == right.orientation -> left.orientation
        left.orientation == Orientation.UNKNOWN -> right.orientation
        else -> left.orientation
    }

    private fun compatible(left: Orientation, right: Orientation): Boolean =
        left == right || left == Orientation.UNKNOWN || right == Orientation.UNKNOWN

    private fun closeEnough(left: FilteredLine, right: FilteredLine, charSize: Float): Boolean {
        val a = left.line.box
        val b = right.line.box
        val orientation = when {
            left.orientation == right.orientation -> left.orientation
            left.orientation == Orientation.UNKNOWN -> right.orientation
            else -> left.orientation
        }
        return when (orientation) {
            Orientation.VERTICAL -> {
                val minH = min(a.height, b.height).coerceAtLeast(1)
                RectMath.gapX(a, b) < Tuning.VERTICAL_GAP_X_FACTOR * charSize &&
                    RectMath.overlapY(a, b).toFloat() / minH > Tuning.VERTICAL_OVERLAP_Y
            }
            Orientation.HORIZONTAL -> {
                val minW = min(a.width, b.width).coerceAtLeast(1)
                RectMath.gapY(a, b) < Tuning.HORIZONTAL_GAP_Y_FACTOR * charSize &&
                    RectMath.overlapX(a, b).toFloat() / minW > Tuning.HORIZONTAL_OVERLAP_X
            }
            Orientation.UNKNOWN -> centerDistance(a, b) < Tuning.UNKNOWN_CENTER_FACTOR * charSize
        }
    }

    private fun centerDistance(a: IntRect, b: IntRect): Float {
        val dx = (a.centerX - b.centerX).toFloat()
        val dy = (a.centerY - b.centerY).toFloat()
        return sqrt(dx * dx + dy * dy)
    }
}
