package app.walsoup.serifu.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed envelope for the billable AI API key at rest.
 *
 * DataStore holds `v1:` + base64(iv || ciphertext) (AES-256-GCM, key in
 * AndroidKeyStore, never in the app process heap at rest). Values without the
 * prefix are pre-encryption installs and read back as-is; the next write
 * re-stores them encrypted. Backup exclusions stay regardless.
 *
 * Failures fail closed on read (empty key, caller treats as unconfigured) and
 * fall back to plaintext on write (better than losing the key); both are logged.
 */
open class ApiKeyCrypto(appContext: Context) {
    private val context = appContext.applicationContext

    sealed interface Result {
        data class Success(val plaintext: String) : Result
        object Empty : Result
        data class Error(val cause: Throwable) : Result
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w(TAG, "API key encryption failed; storing plaintext", t)
            plaintext
        }
    }

    open fun decryptResult(stored: String?): Result {
        if (stored.isNullOrEmpty()) return Result.Empty
        if (!stored.startsWith(PREFIX)) return Result.Success(stored)
        return try {
            val raw = Base64.decode(stored.substring(PREFIX.length), Base64.NO_WRAP)
            if (raw.size <= GCM_IV_BYTES) return Result.Error(IllegalArgumentException("Ciphertext too short"))
            val iv = raw.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = raw.copyOfRange(GCM_IV_BYTES, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            Result.Success(plain)
        } catch (t: Throwable) {
            Log.w(TAG, "API key decryption failed", t)
            Result.Error(t)
        }
    }

    fun decrypt(stored: String?): String = when (val res = decryptResult(stored)) {
        is Result.Success -> res.plaintext
        is Result.Empty, is Result.Error -> ""
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "ApiKeyCrypto"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "serifu_api_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "v1:"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
