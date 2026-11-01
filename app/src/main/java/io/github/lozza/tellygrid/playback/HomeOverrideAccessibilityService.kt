package io.github.lozza.tellygrid.playback

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.lozza.tellygrid.MainActivity

/**
 * Optional launcher fallback for Android TV builds that refuse to persist a
 * third-party Home role. The stock launcher stays installed, while the Home
 * key is swallowed and TellyGrid is opened instead, after the user explicitly
 * enables this service in Accessibility settings.
 *
 * Android's window manager launches the default home on Home key *up*, so both
 * the down and the up event have to be consumed. Letting the up through is what
 * made the stock launcher flash on screen before TellyGrid reappeared.
 */
class HomeOverrideAccessibilityService : AccessibilityService() {
    private var lastLaunchAt = 0L
    private var stockLaunchers: Set<String> = KNOWN_STOCK_LAUNCHERS

    override fun onServiceConnected() {
        super.onServiceConnected()
        stockLaunchers = KNOWN_STOCK_LAUNCHERS + otherHomePackages()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) launchTellyGrid()
        // Consume down, repeats (long press) and up so the system never starts its own Home.
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Safety net for paths that bypass the key, e.g. Back out of an app when
        // the stock launcher is still the system default Home.
        val pkg = event?.packageName?.toString() ?: return
        if (pkg in stockLaunchers) launchTellyGrid(debounce = false)
    }

    override fun onInterrupt() = Unit

    private fun launchTellyGrid(debounce: Boolean = true) {
        val now = SystemClock.uptimeMillis()
        if (debounce && now - lastLaunchAt < DEBOUNCE_MS) return
        lastLaunchAt = now
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                )
            },
        )
    }

    /** Other installed Home apps (stock or OEM launchers), excluding Settings' FallbackHome. */
    private fun otherHomePackages(): Set<String> = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .filterNot { it == packageName || "settings" in it || it == "android" }
            .toSet()
    }.getOrDefault(emptySet())

    private companion object {
        const val DEBOUNCE_MS = 500L
        val KNOWN_STOCK_LAUNCHERS = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
        )
    }
}
