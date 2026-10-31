package app.serifu.core

/**
 * Every numeric threshold in the Serifu build spec. Tuning changes live here,
 * not as inline literals scattered through the pipeline.
 */
object Tuning {
    const val REF_UNIT_DIVISOR = 1080f

    const val SETTLE_DELAY_DEFAULT_MS = 450
    const val SETTLE_DELAY_MIN_MS = 250
    const val SETTLE_DELAY_MAX_MS = 1000
    const val SETTLE_DELAY_STEP_MS = 50

    const val MIN_SCREENSHOT_INTERVAL_API_30_MS = 1050L
    const val MIN_SCREENSHOT_INTERVAL_API_33_MS = 350L
    const val API_SCREENSHOT_INTERVAL_BREAK = 33
    const val CAPTURE_INTERNAL_ERROR_RETRY_MS = 500L
    const val CAPTURE_MAX_RETRIES = 1

    const val OFFSCREEN_SAFE_MS = 50L
    const val FRAME_COMMIT_TIMEOUT_MS = 48L
    const val SELF_DETECT_LATIN_LINES = 2
    const val SELF_DETECT_PREFIX_CHARS = 12

    const val SECURE_BLACK_MEAN_MAX = 6f
    const val SECURE_BLACK_STDDEV_MAX = 3f

    const val FINGERPRINT_COLS = 32
    const val FINGERPRINT_ROWS = 56
    const val SAME_PAGE_MAD = 2.0f
    const val STATUS_BAR_MASK_FRACTION = 0.04f
    const val NAV_BAR_MASK_FRACTION = 0.05f
    const val PAGE_CACHE_SIZE = 12
    const val SCROLL_REUSE_WINDOW_MS = 10_000L
    const val SCROLL_COARSE_FRACTION = 0.6f
    const val SCROLL_FINE_RADIUS_ROWS = 8
    const val SCROLL_FINE_SCALE = 4
    const val SCROLL_ACCEPT_MAD = 3.0f
    const val SCROLL_MIN_OVERLAP_FRACTION = 0.5f
    const val SCROLL_DX_SEARCH_COLS = 4
    const val SHIFT_PREVIEW_ONSCREEN_FRACTION = 0.85f
    const val SHIFT_MATCH_CENTER_REF_UNITS = 16f

    const val OCR_LONG_SIDE_MAX_PX = 2400
    const val LINE_MIN_SIZE_REF_UNITS = 10f
    const val LINE_MIN_CONFIDENCE = 0.35f
    const val ROTATED_ANGLE_DEG = 8f
    const val VERTICAL_ASPECT = 1.35f
    const val VERTICAL_RETILE_CONFIDENCE = 0.6f
    const val VERTICAL_RETILE_COUNT_MISMATCH = 0.4f
    const val VERTICAL_RETILE_GAP_FRACTION = 0.02f
    const val VERTICAL_RETILE_CELL_SPACING = 0.25f
    const val VERTICAL_RETILE_ROW_GAP = 0.5f
    const val VERTICAL_RETILE_CONFIDENCE_GAIN = 0.1f

    const val SIZE_COMPAT_RATIO = 1.8f
    const val VERTICAL_GAP_X_FACTOR = 1.85f
    const val VERTICAL_OVERLAP_Y = 0.30f
    const val HORIZONTAL_GAP_Y_FACTOR = 1.65f
    const val HORIZONTAL_OVERLAP_X = 0.30f
    const val UNKNOWN_CENTER_FACTOR = 1.5f
    const val INK_WALL_BG_PERCENTILE = 0.70f
    const val INK_WALL_RAY_LOW = 0.25f
    const val INK_WALL_RAY_MID = 0.50f
    const val INK_WALL_RAY_HIGH = 0.75f
    const val INK_DELTA = 0.35f
    const val BG_TOLERANCE = 0.12f
    const val INK_RUN_REF_UNITS = 1.5f
    const val OTHER_RUN_MIN = 3
    const val INK_WALL_RAY_HITS_REQUIRED = 2
    const val SAMPLE_RING_PX = 3
    const val PILL_MASK_RING_PX = 4

    const val FURIGANA_SIZE_RATIO = 0.6f
    const val FURIGANA_GAP_FACTOR = 0.6f
    const val FURIGANA_OVERLAP = 0.8f

    const val LINE_GROUP_OVERLAP = 0.5f
    const val READING_BAND_FRACTION = 0.15f
    const val BUBBLE_MERGE_LETTERING_OVERLAP = 0.35f
    const val BUBBLE_MERGE_PATCH_OVERLAP = 0.40f
    const val BUBBLE_MERGE_FALLBACK_OVERLAP = 0.50f

    const val REGION_EXPAND_CHAR_FACTOR = 3f
    const val REGION_EXPAND_UNION_FACTOR = 0.5f
    const val LEAK_PERIMETER_FRACTION = 0.25f
    const val LETTERING_MIDDLE_HEIGHT_FRACTION = 0.70f
    const val LETTERING_SHRINK_FRACTION = 0.06f
    /** Rows narrower than this fraction of the median width are a tail, not the body. */
    const val TAIL_WIDTH_RATIO = 0.72f
    /** Pull the clean in from the detected wall so the balloon outline survives. */
    const val CLEAN_WALL_INSET_PX = 1.25f
    /** How far the clean may reach past the mask to cover antialiased glyph edges. */
    const val CLEAN_COVER_PAD_PX = 3
    /** Same-colour stroke, in frame pixels, that hides the quarter-scale stair. */
    const val CLEAN_FEATHER_PX = 2f
    const val PATCH_PAD_DP = 4f
    const val LUMA_PYRAMID_QUARTER = 4
    const val LUMA_PYRAMID_TINY = 16

    const val FILL_SAMPLE_COUNT = 64
    const val RING_SAMPLE_COUNT = 48
    const val RING_LUMA_PERCENTILE = 0.75f
    const val TEXT_LUMINANCE_SPLIT = 0.45f
    const val FILL_ALPHA = 255
    const val DIALOGUE_RING_STDDEV_MAX = 0.10f
    const val CAPTION_FILL_RATIO = 0.92f
    const val SFX_RING_STDDEV_MIN = 0.18f
    const val SFX_SIZE_FACTOR = 2.5f
    const val INK_ARGB = 0xFF16140F.toInt()
    const val PAPER_ARGB = 0xFFF3EEE3.toInt()

    const val LUMA_R = 54
    const val LUMA_G = 183
    const val LUMA_B = 19

    const val CHAR_BUDGET_MIN = 8
    const val CHAR_BUDGET_REFERENCE_SP = 12f
    const val CHAR_BUDGET_FACTOR = 0.85f
    const val TRANSLATION_CACHE_SIZE = 1000
    const val TRANSLATOR_IDLE_MS = 600_000L
    const val PREVIOUS_PAGE_CONTEXT_LINES = 8
    const val MLKIT_PACK_APPROX_BYTES = 30L * 1024 * 1024
    /**
     * `downloadModelIfNeeded` waits forever when Wi-Fi is required and absent.
     * Past this, the page would stay in the source language with no explanation.
     */
    const val MLKIT_DOWNLOAD_TIMEOUT_MS = 30_000L
    /**
     * A mask this many times the text's own box has flooded the page.
     * A real balloon stays well under it; painting the page covers the art.
     */
    const val REGION_MASK_AREA_FACTOR = 12f
    /** Per-bubble cap once this language pair has completed one translation. */
    const val MLKIT_TRANSLATE_TIMEOUT_MS = 4_000L
    /**
     * First inference after the pack is on disk also loads the model.
     * Four seconds returns the source line, which then looks like a translation.
     */
    const val MLKIT_TRANSLATE_COLD_TIMEOUT_MS = 90_000L
    const val AI_REMOTE_TIMEOUT_MS = 12_000L
    const val EMPTY_PAGE_RETRY_WINDOW_MS = 3_000L
    const val EMPTY_PAGE_RETRY_MAX = 2
    const val EMPTY_PAGE_RETRY_FIRST_MS = 800L
    const val EMPTY_PAGE_RETRY_SECOND_MS = 1_600L

    const val MIN_FONT_SP_DEFAULT = 9
    const val MAX_FONT_SP_DEFAULT = 22
    const val MIN_FONT_SP_FLOOR = 8
    const val MIN_FONT_SP_CEILING = 14
    const val MAX_FONT_SP_FLOOR = 16
    const val MAX_FONT_SP_CEILING = 28
    const val FIT_MAX_ITERATIONS = 7
    const val FIT_EPSILON_PX = 0.5f
    const val LINE_SPACING_MULTIPLIER = 0.95f
    const val OVERFLOW_WIDTH_FRACTION = 0.20f
    const val OVERFLOW_HEIGHT_FRACTION = 0.30f
    const val ABSOLUTE_MIN_FONT_SP = 8
    const val LETTER_SPACING_CAPS = 0.035f
    const val LETTER_SPACING_DIALOGUE = 0.012f
    const val LETTER_SPACING_SHOUT = 0.06f
    const val BALLOON_LEADING_OPEN = 1.0f
    const val BALLOON_LEADING_STACKED = 0.92f
    const val BALLOON_LEADING_TIGHT = 0.88f
    const val DIALOGUE_CORNER_DP = 14f
    const val CAPTION_CORNER_DP = 2f
    const val PATCH_EDGE_STROKE_DP = 1.5f
    const val PATCH_EDGE_STROKE_ALPHA = 128
    const val OVERLAP_OUTLINE_FRACTION = 0.10f
    const val OVERLAP_OUTLINE_DP = 1f
    const val OVERLAP_OUTLINE_CONTRAST = 0.15f
    const val OVERFLOW_GROW_STEP = 1.1f
    const val OVERFLOW_GROW_MAX_STEPS = 24
    const val GLYPH_AREA_FACTOR = 0.5f
    const val TAG_ALPHA = 0.82f
    const val TAG_CORNER_DP = 6f
    const val TAG_PAD_VERTICAL_DP = 4f
    const val TAG_PAD_HORIZONTAL_DP = 8f
    const val SFX_MIN_SP = 11
    const val SFX_MAX_SP = 14
    const val SFX_ANGLE_CLAMP_DEG = 12f
    const val SFX_OUTLINE_STROKE_DP = 3.5f
    const val SHOW_FADE_MS = 90
    const val UPGRADE_CROSSFADE_MS = 150
    const val PEEK_HIDE_MS = 80
    const val PEEK_SHOW_MS = 120
    const val HUD_DISMISS_MS = 6_000L
    const val HUD_WIDTH_DP = 264
    const val HUD_ROW_HEIGHT_DP = 44
    const val HUD_ENTRANCE_MS = 120
    const val HUD_EXIT_MS = 80
    const val PILL_IDLE_DELAY_MS = 3_000L
    const val PILL_ALPHA_FADE_MS = 120
    const val PILL_SIZE_DP = 44
    const val PILL_WIDTH_DP = 44
    const val PILL_HEIGHT_DP = 44
    const val PILL_OFFSCREEN_DP = 10
    const val PILL_CORNER_DP = 22
    const val PILL_SEAL_DP = 44
    const val PILL_SEAL_CORNER_DP = 22
    const val PILL_RING_DP = 2.0f
    const val PILL_RING_INSET_DP = 3
    const val PILL_BREATHE_PERIOD_MS = 2_400
    const val PILL_BREATHE_STOP_MS = 10_000L
    const val PILL_SPINNER_TURN_MS = 900
    const val PILL_DRAG_SCALE = 1.06f
    const val PILL_SPRING_STIFFNESS = 800f
    const val PILL_SPRING_DAMPING = 0.75f
    const val PILL_IDLE_ALPHA_DEFAULT = 0.55f
    const val PILL_IDLE_ALPHA_MIN = 0.3f
    const val PILL_IDLE_ALPHA_MAX = 1.0f
    const val PILL_Y_FRACTION_DEFAULT = 0.62f
    const val TOOLTIP_DISMISS_MS = 4_000L

    const val ONBOARDING_STEPS = 5

    fun minScreenshotIntervalMs(sdkInt: Int): Long =
        if (sdkInt >= API_SCREENSHOT_INTERVAL_BREAK) {
            MIN_SCREENSHOT_INTERVAL_API_33_MS
        } else {
            MIN_SCREENSHOT_INTERVAL_API_30_MS
        }

    fun emptyPageRetryDelayMs(attemptIndex: Int): Long = when (attemptIndex) {
        0 -> EMPTY_PAGE_RETRY_FIRST_MS
        else -> EMPTY_PAGE_RETRY_SECOND_MS
    }

    fun snapSettleDelay(ms: Int): Int {
        val clamped = ms.coerceIn(SETTLE_DELAY_MIN_MS, SETTLE_DELAY_MAX_MS)
        val steps = (clamped - SETTLE_DELAY_MIN_MS + SETTLE_DELAY_STEP_MS / 2) / SETTLE_DELAY_STEP_MS
        return (SETTLE_DELAY_MIN_MS + steps * SETTLE_DELAY_STEP_MS)
            .coerceIn(SETTLE_DELAY_MIN_MS, SETTLE_DELAY_MAX_MS)
    }

    fun snapMinFontSp(sp: Int): Int = sp.coerceIn(MIN_FONT_SP_FLOOR, MIN_FONT_SP_CEILING)

    fun snapMaxFontSp(sp: Int): Int = sp.coerceIn(MAX_FONT_SP_FLOOR, MAX_FONT_SP_CEILING)

    fun snapPillIdleAlpha(alpha: Float): Float = alpha.coerceIn(PILL_IDLE_ALPHA_MIN, PILL_IDLE_ALPHA_MAX)

    fun snapPillYFraction(fraction: Float): Float = fraction.coerceIn(0f, 1f)

    fun refUnit(frameWidth: Int, frameHeight: Int): Float =
        minOf(frameWidth, frameHeight) / REF_UNIT_DIVISOR
}
