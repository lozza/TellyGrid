package io.github.lozza.tellygrid.data

/** Exact content links accepted by the current discovery+ Android TV app. */
object DiscoveryProgrammeLink {
    private val supported = Regex(
        "^https://play\\.discoveryplus\\.com/video/watch/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}/?(?:[?#].*)?$",
    )

    fun verifiedOrNull(channelId: String?, uri: String?): String? {
        if (channelId == null || uri == null) return null
        val provider = ChannelCatalog.byXmlTvId[channelId]?.provider?.id
        return uri.trim().takeIf { provider == ProviderId.DISCOVERY_PLUS && supported.matches(it) }
    }
}
