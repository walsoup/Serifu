package app.serifu.ml

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import app.serifu.core.Tuning
import java.util.Locale

/**
 * Device gate for the Gemma tier (plan.md 11.4).
 * The tier is shown disabled with [Result.reason] when the gate fails.
 */
object DeviceCapability {
    data class Result(val supported: Boolean, val reason: String? = null)

    fun check(context: Context): Result {
        val memory = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryInfoCompat()
        if (memory < Tuning.DEVICE_RAM_MIN_BYTES) {
            return Result(false, ramReason(memory))
        }
        if (!Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }) {
            return Result(false, "Needs a 64-bit ARM processor; this device is not arm64-v8a.")
        }
        val free = freeBytes(context)
        if (free < Tuning.DEVICE_STORAGE_MIN_BYTES) {
            return Result(false, "Needs 3.2 GB of free storage for the Gemma model.")
        }
        return Result(true)
    }

    private fun ramReason(totalBytes: Long): String {
        val gb = totalBytes.toDouble() / 1_000_000_000.0
        val rounded = if (gb >= 9.5) "16 GB" else "${gb.toLong()} GB"
        return "Needs 8 GB of RAM; this phone has $rounded."
    }

    private fun ActivityManager.memoryInfoCompat(): Long {
        val info = ActivityManager.MemoryInfo()
        getMemoryInfo(info)
        return info.totalMem
    }

    private fun freeBytes(context: Context): Long = try {
        val stats = StatFs(context.filesDir.path)
        stats.availableBytes
    } catch (_: Exception) {
        try {
            val stats = StatFs(Environment.getDataDirectory().path)
            stats.availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    internal fun formatGb(bytes: Long): String =
        String.format(Locale.ENGLISH, "%.1f GB", bytes / 1_000_000_000.0)
}
