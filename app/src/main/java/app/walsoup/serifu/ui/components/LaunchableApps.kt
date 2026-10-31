package app.walsoup.serifu.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(intent, 0)
    }
    return resolved.mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        val label = info.loadLabel(context.packageManager).toString()
        val bitmap = info.loadIcon(context.packageManager).toBitmap(48, 48, Bitmap.Config.ARGB_8888)
        LaunchableApp(pkg, label.ifBlank { pkg }, bitmap.asImageBitmap())
    }.distinctBy { it.packageName }
}

/** Loads launchable apps off the main thread; empty until loaded. */
@Composable
fun rememberLaunchableApps(): List<LaunchableApp> {
    val context = LocalContext.current
    val apps by produceState(emptyList(), context) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    return apps
}
