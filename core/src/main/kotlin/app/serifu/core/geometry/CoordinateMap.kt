package app.serifu.core.geometry

/**
 * Screenshot pixels are display pixels for the current rotation.
 * A frame point maps to the canvas by subtracting the overlay's on-screen origin.
 */
object CoordinateMap {
    fun toCanvasX(frameX: Int, originX: Int): Int = frameX - originX

    fun toCanvasY(frameY: Int, originY: Int): Int = frameY - originY
}
