package io.github.lozza.tellygrid.data

object ProviderRegistry {
    val bbc = ProviderApp(
        id = ProviderId.BBC_IPLAYER,
        displayName = "BBC iPlayer",
        packageCandidates = listOf("bbc.iplayer.android", "com.nvidia.bbciplayer"),
        discoveryTerms = listOf("bbc", "iplayer"),
        discoveryUris = listOf("https://www.bbc.co.uk/iplayer"),
    )
    val itvx = ProviderApp(
        id = ProviderId.ITVX,
        displayName = "ITVX",
        packageCandidates = listOf("air.ITVMobilePlayer"),
        discoveryTerms = listOf("itv", "itvx"),
        discoveryUris = listOf("https://www.itv.com/watch"),
    )
    val channel4 = ProviderApp(
        id = ProviderId.CHANNEL_4,
        displayName = "Channel 4",
        packageCandidates = listOf("com.channel4.ondemand"),
        discoveryTerms = listOf("channel 4", "all 4", "channel4"),
        discoveryUris = listOf("https://www.channel4.com/now/c4"),
    )
    val five = ProviderApp(
        id = ProviderId.FIVE,
        displayName = "5",
        packageCandidates = listOf("com.channel5.my5", "com.mobileiq.demand5"),
        discoveryTerms = listOf("channel 5", "my5", "channel5"),
        discoveryUris = listOf("https://www.channel5.com/live/channel-5"),
    )
    val now = ProviderApp(
        id = ProviderId.NOW,
        displayName = "NOW",
        packageCandidates = listOf("com.bskyb.nowtv.beta"),
        discoveryTerms = listOf("now", "nowtv", "bskyb"),
        discoveryUris = listOf("https://www.nowtv.com/watch"),
    )
    val discoveryPlus = ProviderApp(
        id = ProviderId.DISCOVERY_PLUS,
        displayName = "discovery+",
        packageCandidates = listOf(
            "com.discovery.dplay",
            "com.discovery.discoveryplus.mobile",
        ),
        discoveryTerms = listOf("discovery+", "discovery plus", "dplay"),
        discoveryUris = listOf("https://www.discoveryplus.com/gb"),
    )

    val all = listOf(bbc, itvx, channel4, five, now, discoveryPlus)
}
