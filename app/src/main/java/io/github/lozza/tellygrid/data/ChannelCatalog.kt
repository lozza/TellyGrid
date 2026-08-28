package io.github.lozza.tellygrid.data

data class CatalogChannel(
    val xmlTvId: String,
    val number: Int,
    val displayName: String,
    val providerLabel: String,
    val accentArgb: Long,
    val provider: ProviderApp,
    val channelUri: String? = null,
)

object ChannelCatalog {
    private fun channel(
        id: String,
        number: Int,
        name: String,
        label: String,
        accent: Long,
        provider: ProviderApp,
        channelUri: String? = null,
    ) = CatalogChannel(id, number, name, label, accent, provider, channelUri)

    val channels = listOf(
        channel("bbc-one", 1, "BBC One", "BBC iPlayer", 0xFFEF476FL, ProviderRegistry.bbc, "https://www.bbc.co.uk/iplayer/live/bbcone"),
        channel("bbc-two", 2, "BBC Two", "BBC iPlayer", 0xFFF59E0BL, ProviderRegistry.bbc, "https://www.bbc.co.uk/iplayer/live/bbctwo"),
        channel("itv1", 3, "ITV1", "ITVX", 0xFF06D6A0L, ProviderRegistry.itvx, "https://www.itv.com/watch?channel=itv"),
        channel("channel-4", 4, "Channel 4", "Channel 4", 0xFF4CC9F0L, ProviderRegistry.channel4, "https://www.channel4.com/now/c4"),
        channel("five", 5, "5", "5", 0xFFB5179EL, ProviderRegistry.five, "https://www.channel5.com/live/channel-5"),

        channel("sky-showcase", 101, "Sky Showcase", "NOW Entertainment", 0xFF8E7DBEL, ProviderRegistry.now),
        channel("sky-atlantic", 102, "Sky Atlantic", "NOW Entertainment", 0xFF7768AAL, ProviderRegistry.now),
        channel("sky-witness", 103, "Sky Witness", "NOW Entertainment", 0xFF6750A4L, ProviderRegistry.now),
        channel("sky-comedy", 104, "Sky Comedy", "NOW Entertainment", 0xFFB05CC6L, ProviderRegistry.now),
        channel("sky-scifi", 105, "Sky Sci-Fi", "NOW Entertainment", 0xFF536DFEL, ProviderRegistry.now),
        channel("sky-documentaries", 106, "Sky Documentaries", "NOW Entertainment", 0xFF738290L, ProviderRegistry.now),
        channel("sky-crime", 107, "Sky Crime", "NOW Entertainment", 0xFFB83B5EL, ProviderRegistry.now),
        channel("sky-nature", 108, "Sky Nature", "NOW Entertainment", 0xFF44A87CL, ProviderRegistry.now),
        channel("sky-arts", 109, "Sky Arts", "NOW Entertainment", 0xFFE05A8AL, ProviderRegistry.now),
        channel("sky-history", 110, "Sky History", "NOW Entertainment", 0xFFC7924CL, ProviderRegistry.now),
        channel("u-gold", 111, "U&Gold", "NOW Entertainment", 0xFFF2B544L, ProviderRegistry.now),
        channel("u-alibi", 112, "U&Alibi", "NOW Entertainment", 0xFFB85ACAL, ProviderRegistry.now),
        channel("mtv", 113, "MTV", "NOW Entertainment", 0xFFE83283L, ProviderRegistry.now),
        channel("comedy-central", 114, "Comedy Central", "NOW Entertainment", 0xFF6E64C8L, ProviderRegistry.now),
        channel("sky-kids", 115, "Sky Kids", "NOW Entertainment", 0xFFFF8A40L, ProviderRegistry.now),
        channel("sky-mix", 116, "Sky Mix", "NOW Entertainment", 0xFF2CC9B7L, ProviderRegistry.now),

        channel("discovery", 120, "Discovery", "discovery+", 0xFFFF6B35L, ProviderRegistry.discoveryPlus),
        channel("tlc", 121, "TLC", "discovery+", 0xFFE84A8AL, ProviderRegistry.discoveryPlus),
        channel("animal-planet", 122, "Animal Planet", "discovery+", 0xFF4AA96CL, ProviderRegistry.discoveryPlus),
        channel("investigation-discovery", 123, "Investigation Discovery", "discovery+", 0xFFBD3346L, ProviderRegistry.discoveryPlus),
        channel("quest", 124, "Quest", "discovery+", 0xFFDB8B25L, ProviderRegistry.discoveryPlus),
        channel("quest-red", 125, "Quest Red", "discovery+", 0xFFC84265L, ProviderRegistry.discoveryPlus),

        channel("sky-cinema-premiere", 301, "Sky Cinema Premiere", "NOW Cinema", 0xFF5969D8L, ProviderRegistry.now),
        channel("sky-cinema-action", 302, "Sky Cinema Action", "NOW Cinema", 0xFFD14B45L, ProviderRegistry.now),
        channel("sky-cinema-drama", 303, "Sky Cinema Drama", "NOW Cinema", 0xFF9867C5L, ProviderRegistry.now),
        channel("sky-cinema-family", 304, "Sky Cinema Family", "NOW Cinema", 0xFF3AA6C1L, ProviderRegistry.now),
        channel("sky-cinema-greats", 305, "Sky Cinema Greats", "NOW Cinema", 0xFFD0A33EL, ProviderRegistry.now),
        channel("sky-cinema-thriller", 306, "Sky Cinema Thriller", "NOW Cinema", 0xFF7854A8L, ProviderRegistry.now),

        channel("sky-sports-main-event", 401, "Sky Sports Main Event", "NOW Sports", 0xFF2B78E4L, ProviderRegistry.now),
        channel("sky-sports-premier-league", 402, "Sky Sports Premier League", "NOW Sports", 0xFF33A852L, ProviderRegistry.now),
        channel("sky-sports-football", 403, "Sky Sports Football", "NOW Sports", 0xFF23A36DL, ProviderRegistry.now),
        channel("sky-sports-cricket", 404, "Sky Sports Cricket", "NOW Sports", 0xFFE65B3EL, ProviderRegistry.now),
        channel("sky-sports-f1", 405, "Sky Sports F1", "NOW Sports", 0xFFD22F2FL, ProviderRegistry.now),
        channel("sky-sports-golf", 406, "Sky Sports Golf", "NOW Sports", 0xFF23966AL, ProviderRegistry.now),
        channel("sky-sports-action", 407, "Sky Sports Action", "NOW Sports", 0xFFB84FCEL, ProviderRegistry.now),
        channel("sky-sports-news", 408, "Sky Sports News", "NOW Sports", 0xFF2D7EB9L, ProviderRegistry.now),
    )

    val byXmlTvId = channels.associateBy { it.xmlTvId }
}
