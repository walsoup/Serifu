package app.serifu.core.geometry

data class IntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun intersect(other: IntRect): IntRect {
        val l = maxOf(left, other.left)
        val t = maxOf(top, other.top)
        val r = minOf(right, other.right)
        val b = minOf(bottom, other.bottom)
        return if (r <= l || b <= t) Empty else IntRect(l, t, r, b)
    }

    fun union(other: IntRect): IntRect {
        if (isEmpty) return other
        if (other.isEmpty) return this
        return IntRect(
            minOf(left, other.left),
            minOf(top, other.top),
            maxOf(right, other.right),
            maxOf(bottom, other.bottom),
        )
    }

    fun expand(px: Int): IntRect = IntRect(left - px, top - px, right + px, bottom + px)

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    fun contains(other: IntRect): Boolean =
        !other.isEmpty && other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    companion object {
        val Empty = IntRect(0, 0, 0, 0)
    }
}

object RectMath {
    fun gapX(a: IntRect, b: IntRect): Int = when {
        a.right < b.left -> b.left - a.right
        b.right < a.left -> a.left - b.right
        else -> 0
    }

    fun gapY(a: IntRect, b: IntRect): Int = when {
        a.bottom < b.top -> b.top - a.bottom
        b.bottom < a.top -> a.top - b.bottom
        else -> 0
    }

    fun overlapX(a: IntRect, b: IntRect): Int =
        (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0)

    fun overlapY(a: IntRect, b: IntRect): Int =
        (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0)
}
