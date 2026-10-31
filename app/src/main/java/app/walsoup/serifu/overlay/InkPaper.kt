package app.walsoup.serifu.overlay

import android.graphics.Color

/** Ink and Paper colours for overlay Views, which draw outside Compose (no theme dependency). */
object InkPaper {
    const val PAPER = 0xFFF3EEE3.toInt()
    const val INK = 0xFF16140F.toInt()
    const val HANKO = 0xFFC8322B.toInt()
    const val ON_HANKO = 0xFFFFF7F2.toInt()
    const val AMBER = 0xFFE0A100.toInt()
    const val SCREENTONE = 0xFF8A857A.toInt()
    const val CYAN = 0xFF00ACC1.toInt()
    const val MAGENTA = 0xFFE040FB.toInt()
    const val DEBUG_GREY = 0xFF9E9E9E.toInt()
    const val DEBUG_RED = 0xFFE53935.toInt()
    const val DEBUG_GREEN = 0xFF43A047.toInt()
    const val TRANSPARENT = Color.TRANSPARENT
}
