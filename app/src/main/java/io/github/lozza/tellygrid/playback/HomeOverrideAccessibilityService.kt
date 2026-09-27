package io.github.lozza.tellygrid.playback

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.lozza.tellygrid.MainActivity

/**
 * Optional launcher fallback for Android TV builds that refuse to persist a
 * third-party Home role. It mirrors the approach used by Projectivy: the
 * stock launcher remains installed, while Home is redirected back to TellyGrid
 * after the user explicitly enables this service in Accessibility settings.
 */
class HomeOverrideAccessibilityService : AccessibilityService() {
    private var lastLaunchAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() == STOCK_LAUNCHER_PACKAGE) launchTellyGrid()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_HOME) {
            launchTellyGrid()
            return true
        }
        return false
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun launchTellyGrid() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            val now = SystemClock.uptimeMillis()
            if (now - lastLaunchAt < 700L) return@postDelayed
            lastLaunchAt = now
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
        }, 350L)
    }

    private companion object {
        const val STOCK_LAUNCHER_PACKAGE = "com.google.android.tvlauncher"
    }
}
