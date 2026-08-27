package io.github.lozza.tellygrid.data

object ProviderRegistry {
    val bbc = ProviderApp(
        id = ProviderId.BBC_IPLAYER,
        displayName = "BBC iPlayer",
        packageCandidates = listOf("bbc.iplayer.android", "com.nvidia.bbciplayer"),
        playStorePackage = "bbc.iplayer.android",
    )
    val itvx = ProviderApp(
        id = ProviderId.ITVX,
        displayName = "ITVX",
        packageCandidates = listOf("air.ITVMobilePlayer"),
        playStorePackage = "air.ITVMobilePlayer",
    )
    val channel4 = ProviderApp(
        id = ProviderId.CHANNEL_4,
        displayName = "Channel 4",
        packageCandidates = listOf("com.channel4.ondemand"),
        playStorePackage = "com.channel4.ondemand",
    )
    val five = ProviderApp(
        id = ProviderId.FIVE,
        displayName = "5",
        packageCandidates = listOf("com.channel5.my5", "com.mobileiq.demand5"),
        playStorePackage = "com.channel5.my5",
    )
    val now = ProviderApp(
        id = ProviderId.NOW,
        displayName = "NOW",
        packageCandidates = listOf("com.bskyb.nowtv.beta"),
        playStorePackage = "com.bskyb.nowtv.beta",
    )
    val discoveryPlus = ProviderApp(
        id = ProviderId.DISCOVERY_PLUS,
        displayName = "discovery+",
        packageCandidates = listOf(
            "com.discovery.dplay",
            "com.discovery.discoveryplus.mobile",
        ),
        playStorePackage = "com.discovery.dplay",
    )
}
