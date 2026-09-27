package io.github.lozza.tellygrid.playback

import android.content.Context

class AccessibilityDisclosurePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("accessibility_disclosure", Context.MODE_PRIVATE)

    fun hasSeenDiscoveryDisclosure(): Boolean =
        preferences.getBoolean("discovery_channel_control_seen", false)

    fun markDiscoveryDisclosureSeen() {
        preferences.edit().putBoolean("discovery_channel_control_seen", true).apply()
    }
}
