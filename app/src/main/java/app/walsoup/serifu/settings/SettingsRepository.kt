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

    /** Browsers stay listed as known but are never auto-enabled: any web page
     * would otherwise get capture in AUTO mode on first run. Explicit opt-in. */
    val browsers: Set<String> = setOf("com.android.chrome", "org.mozilla.firefox")

    /** Auto-seeded on first run: manga readers only, never browsers. */
    val autoSeed: List<String> = packages.filter { it !in browsers }

    fun installed(context: Context): Set<String> = autoSeed.filter { pkg ->
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
    private val crypto: ApiKeyCrypto = ApiKeyCrypto(context),
) {
    val settings: StateFlow<Settings?> = context.serifuSettingsDataStore.data
        .map { prefs -> prefs.toSettings(KnownReaders.installed(context), crypto::decrypt) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun update(transform: (Settings) -> Settings) {
        context.serifuSettingsDataStore.edit { prefs ->
            val rawStored = prefs[SettingsKeys.aiApiKey]
            val decryptResult = crypto.decryptResult(rawStored)
            val currentPlaintext = when (decryptResult) {
                is ApiKeyCrypto.Result.Success -> decryptResult.plaintext
                is ApiKeyCrypto.Result.Empty, is ApiKeyCrypto.Result.Error -> ""
            }
            val current = prefs.toSettings(KnownReaders.installed(context)) { currentPlaintext }
            val next = transform(current)

            // If decryption failed, and the caller didn't explicitly change the key from currentPlaintext (""),
            // preserve the raw stored ciphertext rather than overwriting it with an encrypted blank string.
            val shouldPreserveKey = decryptResult is ApiKeyCrypto.Result.Error && next.aiApiKey == currentPlaintext

            next.writeTo(prefs) { plainKey ->
                if (shouldPreserveKey) rawStored.orEmpty() else crypto.encrypt(plainKey)
            }
        }
    }
}

fun sourceLabel(language: SourceLanguage): String = language.englishName
