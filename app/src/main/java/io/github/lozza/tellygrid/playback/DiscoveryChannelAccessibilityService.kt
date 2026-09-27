package io.github.lozza.tellygrid.playback

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Opt-in, gesture-only discovery+ navigation.
 *
 * This service is restricted to discovery+'s package by its XML configuration.
 * It never requests or traverses accessibility node content and only dispatches
 * a short gesture sequence immediately after the user chooses a discovery+
 * channel in TellyGrid.
 */
class DiscoveryChannelAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastDiscoveryEventAt = 0L
    private var queuedAt = 0L

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() == DiscoveryChannelAutomation.PACKAGE_NAME) {
            lastDiscoveryEventAt = System.currentTimeMillis()
            if (queuedAt != 0L) Log.i(TAG, "Saw discovery+ event type=${event.eventType}")
        }
    }

    override fun onInterrupt() = cancelPending()

    override fun onDestroy() {
        if (instance === this) instance = null
        cancelPending()
        super.onDestroy()
    }

    private fun queue(channelId: String) {
        cancelPending()
        val plan = DiscoveryChannelAutomation.planFor(channelId)
        queuedAt = System.currentTimeMillis()
        Log.i(TAG, "Queued channel=$channelId swipe=${plan.swipeFirst} x=${plan.tapXFraction}")

        // The root discovery+ link first normalises the single-activity app to Home.
        // Cold launches on lower-powered Android TV hardware need several seconds.
        handler.postDelayed({
            if (lastDiscoveryEventAt < queuedAt) {
                // A warm single-activity discovery+ task does not reliably emit a new
                // window event on some Android TV 11 builds. The queue itself can only
                // be created by an explicit channel selection followed by a successful
                // package-scoped discovery+ launch, so continue after the launch delay.
                Log.i(TAG, "No new discovery+ event; continuing after explicit launch")
            }
            Log.i(TAG, "Tapping discovery+ Browse")
            tap(0.035f, 0.746f) {
                handler.postDelayed({
                    if (plan.swipeFirst) {
                        swipe(0.89f, 0.315f, 0.26f, 0.315f) {
                            handler.postDelayed({ finishOnChannel(plan) }, 1_200L)
                        }
                    } else {
                        finishOnChannel(plan)
                    }
                }, 2_800L)
            }
        }, 6_500L)
    }

    private fun finishOnChannel(plan: DiscoveryGesturePlan) {
        val x = plan.tapXFraction
        if (x == null) {
            cancelPending() // Browse-only fallback for a channel absent from the row.
            return
        }
        tap(x, 0.315f) { cancelPending() }
    }

    private fun tap(xFraction: Float, yFraction: Float, onComplete: () -> Unit) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * xFraction
        val y = metrics.heightPixels * yFraction
        Log.i(TAG, "Tap x=$x y=$y display=${metrics.widthPixels}x${metrics.heightPixels}")
        val path = Path().apply {
            moveTo(x, y)
            // Some vendor Android TV builds cancel a move-only (zero-length) path.
            lineTo(x + 1f, y + 1f)
        }
        dispatch(path, 120L, onComplete)
    }

    private fun swipe(
        startXFraction: Float,
        startYFraction: Float,
        endXFraction: Float,
        endYFraction: Float,
        onComplete: () -> Unit,
    ) {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels * startXFraction, metrics.heightPixels * startYFraction)
            lineTo(metrics.widthPixels * endXFraction, metrics.heightPixels * endYFraction)
        }
        dispatch(path, 600L, onComplete)
    }

    private fun dispatch(path: Path, durationMs: Long, onComplete: () -> Unit) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Gesture completed")
                    onComplete()
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Gesture cancelled by Android")
                    cancelPending()
                }
            },
            handler,
        )
        Log.i(TAG, "Gesture accepted=$accepted")
        if (!accepted) cancelPending()
    }

    private fun cancelPending() {
        handler.removeCallbacksAndMessages(null)
        queuedAt = 0L
    }

    companion object {
        private const val TAG = "TellyGridDiscovery"

        @Volatile
        private var instance: DiscoveryChannelAccessibilityService? = null

        fun queueChannel(channelId: String): Boolean {
            val service = instance ?: run {
                Log.w(TAG, "Cannot queue channel=$channelId: service instance unavailable")
                return false
            }
            if (!isGestureInjectionSupported(service)) {
                Log.w(TAG, "Cannot queue channel=$channelId: no touchscreen input source")
                return false
            }
            service.queue(channelId)
            return true
        }

        fun cancelQueuedChannel() {
            instance?.cancelPending()
        }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, DiscoveryChannelAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            return splitter.any { ComponentName.unflattenFromString(it) == expected }
        }

        fun isGestureInjectionSupported(context: Context): Boolean =
            context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    }
}
