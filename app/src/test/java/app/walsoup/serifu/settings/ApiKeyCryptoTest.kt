package app.walsoup.serifu.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ApiKeyCryptoTest {
    private fun crypto() = ApiKeyCrypto(RuntimeEnvironment.getApplication())

    @Test
    fun roundTripPreservesTheKey() {
        val crypto = crypto()
        assertEquals("sk-test-123", crypto.decrypt(crypto.encrypt("sk-test-123")))
        assertEquals("", crypto.decrypt(crypto.encrypt("")))
    }

    @Test
    fun legacyPlaintextReadsBackAsIs() {
        val crypto = crypto()
        assertEquals("plain-old-key", crypto.decrypt("plain-old-key"))
        assertEquals("", crypto.decrypt(null))
        assertEquals("", crypto.decrypt(""))
    }

    @Test
    fun corruptCiphertextFailsClosed() {
        val crypto = crypto()
        assertEquals("", crypto.decrypt("v1:not-valid-base64!!!"))
        assertEquals("", crypto.decrypt("v1:AAAA"))
        assertTrue(crypto.decryptResult("v1:AAAA") is ApiKeyCrypto.Result.Error)
    }

    @Test
    fun updatePreservesCiphertextWhenDecryptionFails() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val crypto = crypto()
        val realCiphertext = crypto.encrypt("sk-secret-key-12345")

        // A crypto instance whose decryption simulates a transient Keystore error.
        val transientFailingCrypto = object : ApiKeyCrypto(app) {
            override fun decryptResult(stored: String?): Result {
                return Result.Error(IllegalStateException("Keystore transient error"))
            }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = SettingsRepository(app, scope, crypto)

        // Seed with a valid key.
        repository.update { it.copy(aiApiKey = "sk-secret-key-12345") }

        // Now an update occurs while decryption fails.
        val repoWithFailingCrypto = SettingsRepository(app, scope, transientFailingCrypto)
        repoWithFailingCrypto.update { it.copy(dynamicColor = true) }

        // The stored ciphertext must not have been erased/overwritten with an empty key.
        val repoAfterRecovery = SettingsRepository(app, scope, crypto)
        assertEquals("sk-secret-key-12345", repoAfterRecovery.settings.value?.aiApiKey)
    }
}
