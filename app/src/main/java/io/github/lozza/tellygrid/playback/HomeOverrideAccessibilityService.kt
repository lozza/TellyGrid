package io.github.lozza.tellygrid.playback

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
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
    /** A remapped button that is still held down; its repeats and release are swallowed. */
    private var heldKey: Int? = null
    private var stockLaunchers: Set<String> = KNOWN_STOCK_LAUNCHERS

    override fun onServiceConnected() {
        super.onServiceConnected()
        stockLaunchers = KNOWN_STOCK_LAUNCHERS + otherHomePackages()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) launchTellyGrid()
            // Consume down, repeats (long press) and up so the system never starts its own Home.
            return true
        }
        return handleRemoteButton(event)
    }

    /**
     * Buttons the user has mapped in Settings > Remote buttons (e.g. Settings opens the Guide).
     * Like Home, a handled button's repeats and release are swallowed too, so the TV or the
     * app on screen never sees half a press.
     */
    private fun handleRemoteButton(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == heldKey) {
            if (event.action == KeyEvent.ACTION_UP) heldKey = null
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        if (!RemoteButtons.canRemap(keyCode)) return false

        RemoteButtonCapture.listener?.let { listener ->
            RemoteButtonCapture.listener = null
            heldKey = keyCode
            listener(keyCode)
            return true
        }
        val destination = RemoteButtonStore.destination(this, keyCode) ?: return false
        heldKey = keyCode
        openDestination(destination)
        return true
    }

    private fun openDestination(destination: ButtonDestination) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra(MainActivity.EXTRA_DESTINATION, destination.name)
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

    companion object {
        /** True when the user has switched this service on in Android's Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, HomeOverrideAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            return splitter.any { ComponentName.unflattenFromString(it) == expected }
        }

        private const val DEBOUNCE_MS = 500L
        private val KNOWN_STOCK_LAUNCHERS = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
        )
    }
}
