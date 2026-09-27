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

    /**
     * Live-channel links copied from play.discoveryplus.com (UK) and verified on the
     * reference TV: the TV app plays them directly. Format: /channel/watch/<channel>/<edit>.
     */
    private val liveChannelUris = mapOf(
        "discovery" to "https://play.discoveryplus.com/channel/watch/f20458c5-05ae-5cc4-9a2d-7fa752d7ca76/5183151d-d5e4-5a2a-b0c5-5a9561e157a5",
        "quest" to "https://play.discoveryplus.com/channel/watch/fbd8b745-7692-50a2-b3e5-8525a45610d6/f9f0f3a0-f0c3-5f34-b3c6-680d8ac4cf61",
        "tlc" to "https://play.discoveryplus.com/channel/watch/4e47f244-45d5-5fff-89c5-82c47ac81228/0de4393b-103a-5bb8-bd59-6959dd0faf49",
        "investigation-discovery" to "https://play.discoveryplus.com/channel/watch/05196e5f-bca5-5c02-b833-d6e4264edf23/0a79d4f7-2045-5a93-b4c0-6965177760a6",
        "really" to "https://play.discoveryplus.com/channel/watch/13c0e802-8d2c-5820-8d46-f90d16899f5e/8ca642a2-22f6-5928-ad33-fa1ed1d08a36",
        "quest-red" to "https://play.discoveryplus.com/channel/watch/3f3e6a4d-c8dd-5a3c-a720-19f976824cd5/9e6ccab8-a9ee-518f-a45e-eb6c3e9157a1",
        "animal-planet" to "https://play.discoveryplus.com/channel/watch/221a9c89-eb33-5f83-a89a-1a1c290bdbe3/06838ef9-c54d-58a8-ba5c-9b8e53490f6b",
        "food-network" to "https://play.discoveryplus.com/channel/watch/17fe9d91-dae7-532c-8e82-e798902f127a/db1c6370-670c-5c3c-9d3c-c599fbd9298d",
        "dmax" to "https://play.discoveryplus.com/channel/watch/ec1c631b-bce2-5ecc-be40-886b66aed1d0/42e33b7f-b747-5c6a-97b9-c1dcfc93878c",
        "discovery-science" to "https://play.discoveryplus.com/channel/watch/2673401c-ce4b-5a43-9962-aff676cf912a/9aa48cd3-e0c4-5e95-bbc6-dab72f9432e9",
        "discovery-turbo" to "https://play.discoveryplus.com/channel/watch/05e2f76d-d1d9-568c-8116-e65cec3b03b4/f860e15c-0498-5165-823a-25c02cee02b6",
        "discovery-history" to "https://play.discoveryplus.com/channel/watch/6457ba2e-34bf-55b9-a7d6-d546a7c74f6c/c81735cc-db46-599e-8226-e0ad700d42d4",
    )

    fun liveChannelUri(channelId: String): String? = liveChannelUris[channelId]

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
