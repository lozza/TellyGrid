package io.github.lozza.tellygrid.playback

/**
 * Coordinates are fractions of the discovery+ TV app's 16:9 viewport.
 *
 * The discovery+ Android TV app does not publish stable live-channel deep links,
 * but its TV UI accepts accessibility gestures. These positions were verified
 * against discovery+ 21.10.0.73. Unknown channels deliberately stop on Browse.
 */
data class DiscoveryGesturePlan(
    val tapXFraction: Float?,
    val swipeFirst: Boolean = false,
)

object DiscoveryChannelAutomation {
    const val PACKAGE_NAME = "com.discovery.dplay"
    const val HOME_URI = "https://play.discoveryplus.com/"

    private val channelIndexes = mapOf(
        "discovery" to 0,
        "tlc" to 1,
        "quest" to 2,
        "investigation-discovery" to 3,
        "quest-red" to 4,
        "animal-planet" to 5,
        "food-network" to 6,
        "dmax" to 7,
        "discovery-science" to 8,
        "discovery-turbo" to 9,
        "discovery-history" to 10,
    )

    fun planFor(channelId: String): DiscoveryGesturePlan {
        val index = channelIndexes[channelId] ?: return DiscoveryGesturePlan(tapXFraction = null)
        return when (index) {
            0 -> DiscoveryGesturePlan(tapXFraction = 0.30f)
            in 1..3 -> DiscoveryGesturePlan(tapXFraction = 0.609f + ((index - 1) * 0.133f))
            else -> DiscoveryGesturePlan(
                tapXFraction = 0.080f + ((index - 4) * 0.133f),
                swipeFirst = true,
            )
        }
    }

    fun hasDirectGesture(channelId: String): Boolean = planFor(channelId).tapXFraction != null
}
