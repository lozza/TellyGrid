package io.github.lozza.tellygrid.data

object ProviderRegistry {
    val bbc = ProviderApp(
        id = ProviderId.BBC_IPLAYER,
        displayName = "BBC iPlayer",
        packageCandidates = listOf("uk.co.freeview.bbc", "bbc.iplayer.android", "com.nvidia.bbciplayer"),
        discoveryTerms = listOf("bbc", "iplayer"),
        discoveryUris = listOf("https://www.bbc.co.uk/iplayer"),
    )
    val itvx = ProviderApp(
        id = ProviderId.ITVX,
        displayName = "ITVX",
        packageCandidates = listOf("uk.co.freeview.itv", "air.ITVMobilePlayer"),
        discoveryTerms = listOf("itv", "itvx"),
        discoveryUris = listOf("https://www.itv.com/watch"),
    )
    val channel4 = ProviderApp(
        id = ProviderId.CHANNEL_4,
        displayName = "Channel 4",
        packageCandidates = listOf("uk.co.freeview.ch4_vod", "com.channel4.ondemand"),
        discoveryTerms = listOf("channel 4", "all 4", "channel4"),
        discoveryUris = listOf("https://www.channel4.com/now/c4"),
    )
    val five = ProviderApp(
        id = ProviderId.FIVE,
        displayName = "5",
        packageCandidates = listOf("uk.co.freeview.ch5", "com.channel5.my5", "com.mobileiq.demand5"),
        discoveryTerms = listOf("channel 5", "my5", "channel5"),
        discoveryUris = listOf("https://www.channel5.com/live/channel-5"),
    )
    val u = ProviderApp(
        id = ProviderId.U,
        displayName = "U",
        packageCandidates = listOf("uk.co.freeview.uktv"),
        discoveryTerms = listOf("u&", "uktv", "dave", "drama", "yesterday"),
        discoveryUris = emptyList(),
    )
    val now = ProviderApp(
        id = ProviderId.NOW,
        displayName = "NOW",
        packageCandidates = listOf("com.bskyb.nowtv.beta"),
        discoveryTerms = listOf("now", "nowtv", "bskyb"),
        discoveryUris = listOf("https://tv.client.ott.sky.com/"),
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
    val hboMax = ProviderApp(
        id = ProviderId.HBO_MAX,
        displayName = "HBO Max",
        packageCandidates = listOf("com.wbd.stream"),
        discoveryTerms = listOf("hbo max", "hbomax", "wbd"),
        discoveryUris = listOf("https://www.hbomax.com/gb/en"),
    )

    /** Official UK Pluto app. Channel links remain deliberately unverified. */
    val pluto = ProviderApp(
        id = ProviderId.PLUTO_TV,
        displayName = "Pluto TV",
        packageCandidates = listOf("tv.pluto.android"),
        discoveryTerms = listOf("pluto", "pluto tv"),
        discoveryUris = listOf("https://pluto.tv/"),
    )

    val all = listOf(bbc, itvx, channel4, five, u, now, discoveryPlus, hboMax, pluto)
}
