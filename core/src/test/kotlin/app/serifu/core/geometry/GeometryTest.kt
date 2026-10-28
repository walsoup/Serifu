package app.serifu.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectMathTest {
    @Test
    fun gapAndOverlap() {
        val a = IntRect(0, 0, 10, 20)
        val b = IntRect(14, 5, 30, 18)
        assertEquals(4, RectMath.gapX(a, b))
        assertEquals(0, RectMath.gapY(a, b))
        assertEquals(0, RectMath.overlapX(a, b))
        assertEquals(13, RectMath.overlapY(a, b))
        assertEquals(0, RectMath.gapX(a, IntRect(10, 0, 12, 4)))
    }

    @Test
    fun expandClipAndUnion() {
        val frame = IntRect(0, 0, 100, 50)
        val grown = IntRect(2, 2, 8, 8).expand(4).intersect(frame)
        assertEquals(IntRect(0, 0, 12, 12), grown)
        val union = IntRect(0, 0, 2, 2).union(IntRect(5, 6, 9, 10))
        assertEquals(IntRect(0, 0, 9, 10), union)
        assertTrue(frame.contains(10, 10))
        assertFalse(frame.contains(100, 10))
    }
}

class CoordinateMapTest {
    @Test
    fun subtractsOverlayOrigin() {
        assertEquals(90, CoordinateMap.toCanvasX(100, 10))
        assertEquals(180, CoordinateMap.toCanvasY(200, 20))
    }
}
