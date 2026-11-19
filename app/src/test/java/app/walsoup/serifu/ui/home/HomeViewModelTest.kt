package app.walsoup.serifu.ui.home

import app.walsoup.serifu.settings.Settings
import app.walsoup.serifu.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HomeViewModelTest {
    @Test
    fun constructsBeforeSettingsEmit() {
        val repository = SettingsRepository(
            RuntimeEnvironment.getApplication(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        // Must not throw when the store has not emitted yet; seeds a default or emitted settings.
        val viewModel = HomeViewModel(repository)
        val expected = repository.settings.value ?: Settings()
        assertEquals(expected, viewModel.settings.value)
    }
}
