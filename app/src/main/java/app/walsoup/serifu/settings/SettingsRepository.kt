package app.walsoup.serifu.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import app.serifu.core.model.SourceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.serifuSettingsDataStore by preferencesDataStore(name = "serifu_settings")

object KnownReaders {
    val packages: List<String> = listOf(
        "app.mihon",
        "org.koitharu.kotatsu",
        "eu.kanade.tachiyomi.sy",
        "eu.kanade.tachiyomi.j2k",
        "com.android.chrome",
        "org.mozilla.firefox",
    )

    fun installed(context: Context): Set<String> = packages.filter { pkg ->
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }.toSet()
}

class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
) {
    val settings: StateFlow<Settings?> = context.serifuSettingsDataStore.data
        .map { prefs -> prefs.toSettings(KnownReaders.installed(context)) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun update(transform: (Settings) -> Settings) {
        context.serifuSettingsDataStore.edit { prefs ->
            val current = prefs.toSettings(KnownReaders.installed(context))
            transform(current).writeTo(prefs)
        }
    }
}

fun sourceLabel(language: SourceLanguage): String = language.englishName
