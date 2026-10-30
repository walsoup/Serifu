package app.serifu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenStateReceiver(
    private val onScreenOff: () -> Unit,
    private val onUserPresent: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> onUserPresent()
        }
    }
}
