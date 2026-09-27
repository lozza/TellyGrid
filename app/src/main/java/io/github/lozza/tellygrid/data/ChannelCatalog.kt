package io.github.lozza.tellygrid.data

data class CatalogChannel(
    /** Stable TellyGrid identity; never use a provider's mutable XMLTV id as state. */
    val id: String,
    val xmlTvId: String,
    val number: Int,
    val displayName: String,
    val providerLabel: String,
    val accentArgb: Long,
    val logoUri: String?,
    val provider: ProviderApp,
    val channelUri: String? = null,
    val terrestrialLcn: Int? = null,
    val category: GuideCategory = GuideCategory.ENTERTAINMENT,
)

object ChannelCatalog {
    private fun nowChannelUri(serviceKey: Int): String =
        "https://tv.client.ott.sky.com/?deeplinkData=%7B%22serviceKey%22%3A%22$serviceKey%22%2C%22type%22%3A%22LINEAR_CHANNEL%22%2C%22action%22%3A%22PLAY%22%7D"

    /**
     * The installed Android TV app accepts this in its explicit EntryPoint
     * activity. The UUID is Pluto's public channel identity from its UK EPG,
     * not a stream URL. COPS was verified on the reference Philips TV.
     */
    private fun plutoChannelUri(channelId: String): String =
        "https://pluto.tv/gb_ie/watch/live-tv/$channelId"

    private fun channel(
        id: String,
        number: Int,
        name: String,
        label: String,
        accent: Long,
        provider: ProviderApp,
        channelUri: String? = null,
        terrestrialLcn: Int? = null,
        category: GuideCategory = GuideCategory.ENTERTAINMENT,
        xmlTvId: String = id,
    ) = CatalogChannel(
        id = id,
        xmlTvId = xmlTvId,
        number = number,
        displayName = name,
        providerLabel = label,
        accentArgb = accent,
        logoUri = ChannelLogoCatalog.logoFor(id),
        provider = provider,
        channelUri = channelUri,
        terrestrialLcn = terrestrialLcn,
        category = category,
    )

    val channels = listOf(
        channel("bbc-one", 1, "BBC One", "BBC iPlayer", 0xFFEF476FL, ProviderRegistry.bbc, "https://www.bbc.co.uk/iplayer/live/bbcone", terrestrialLcn = 1),
        channel("bbc-two", 2, "BBC Two", "BBC iPlayer", 0xFFF59E0BL, ProviderRegistry.bbc, "https://www.bbc.co.uk/iplayer/live/bbctwo", terrestrialLcn = 2),
        channel("itv1", 3, "ITV1", "ITVX", 0xFF06D6A0L, ProviderRegistry.itvx, "https://www.itv.com/watch?channel=itv", terrestrialLcn = 3),
        channel("channel-4", 4, "Channel 4", "Channel 4", 0xFF4CC9F0L, ProviderRegistry.channel4, "https://www.channel4.com/now/c4", terrestrialLcn = 4),
        channel("five", 5, "5", "5", 0xFFB5179EL, ProviderRegistry.five, "https://www.channel5.com/live/channel-5", terrestrialLcn = 5),
        channel("itv2", 6, "ITV2", "ITVX", 0xFF06D6A0L, ProviderRegistry.itvx, "https://www.itv.com/watch?channel=itv2", terrestrialLcn = 6),
        channel("bbc-four", 9, "BBC Four", "BBC iPlayer", 0xFFF59E0BL, ProviderRegistry.bbc, "https://www.bbc.co.uk/iplayer/live/bbcfour", terrestrialLcn = 9),
        channel("itv3", 10, "ITV3", "ITVX", 0xFF09B89BL, ProviderRegistry.itvx, "https://www.itv.com/watch?channel=itv3", terrestrialLcn = 10),
        channel("sky-mix", 11, "Sky Mix", "NOW Entertainment", 0xFF2CC9B7L, ProviderRegistry.now, nowChannelUri(1831), terrestrialLcn = 11),
        channel("tlc", 12, "TLC", "discovery+", 0xFFE84A8AL, ProviderRegistry.discoveryPlus, terrestrialLcn = 12),
        channel("e4", 13, "E4", "Channel 4", 0xFF49B8E8L, ProviderRegistry.channel4, terrestrialLcn = 13),
        channel("film4", 14, "Film4", "Channel 4", 0xFF6C8ACEL, ProviderRegistry.channel4, terrestrialLcn = 14, category = GuideCategory.MOVIES),
        channel("quest", 17, "Quest", "discovery+", 0xFFDB8B25L, ProviderRegistry.discoveryPlus, terrestrialLcn = 17),
        channel("more4", 18, "More4", "Channel 4", 0xFF338FA8L, ProviderRegistry.channel4, terrestrialLcn = 18),
        channel("u-dave", 19, "U&Dave", "U", 0xFF31B6A1L, ProviderRegistry.u, terrestrialLcn = 19),
        channel("u-drama", 20, "U&Drama", "U", 0xFF9A6BC1L, ProviderRegistry.u, terrestrialLcn = 20),
        channel("5usa", 21, "5USA", "5", 0xFFB5179EL, ProviderRegistry.five, terrestrialLcn = 21),
        channel("bbc-three", 23, "BBC Three", "BBC iPlayer", 0xFFEF476FL, ProviderRegistry.bbc, "https://www.bbc.co.uk/iplayer/live/bbcthree", terrestrialLcn = 23),
        channel("u-w", 25, "U&W", "U", 0xFFE66B8CL, ProviderRegistry.u, terrestrialLcn = 25),
        channel("itv4", 26, "ITV4", "ITVX", 0xFF0EA97AL, ProviderRegistry.itvx, "https://www.itv.com/watch?channel=itv4", terrestrialLcn = 26),
        channel("u-yesterday", 27, "U&Yesterday", "U", 0xFFD3A338L, ProviderRegistry.u, terrestrialLcn = 27, category = GuideCategory.DOCUMENTARY),
        channel("itv-quiz", 28, "ITV Quiz", "ITVX", 0xFF157FB5L, ProviderRegistry.itvx, terrestrialLcn = 28),
        channel("5star", 32, "5STAR", "5", 0xFFD43D80L, ProviderRegistry.five, terrestrialLcn = 32),
        channel("5action", 33, "5ACTION", "5", 0xFFAD493DL, ProviderRegistry.five, terrestrialLcn = 33),
        channel("sky-arts", 36, "Sky Arts", "NOW Entertainment", 0xFFE05A8AL, ProviderRegistry.now, nowChannelUri(4063), terrestrialLcn = 36),
        channel("dmax", 39, "DMAX", "discovery+", 0xFFFF6B35L, ProviderRegistry.discoveryPlus, terrestrialLcn = 39),
        channel("quest-red", 40, "Quest Red", "discovery+", 0xFFC84265L, ProviderRegistry.discoveryPlus, terrestrialLcn = 40),
        channel("food-network", 43, "Food Network", "discovery+", 0xFFF09A4AL, ProviderRegistry.discoveryPlus, terrestrialLcn = 43),
        channel("really", 44, "Really", "discovery+", 0xFFC9507EL, ProviderRegistry.discoveryPlus, terrestrialLcn = 44),
        channel("5select", 46, "5SELECT", "5", 0xFF7D67B0L, ProviderRegistry.five, terrestrialLcn = 46),
        channel("4seven", 49, "4seven", "Channel 4", 0xFF4CC9F0L, ProviderRegistry.channel4, terrestrialLcn = 49),
        channel("u-eden", 57, "U&Eden", "U", 0xFF4F9B64L, ProviderRegistry.u, terrestrialLcn = 57, category = GuideCategory.DOCUMENTARY),
        channel("cbbc", 201, "CBBC", "BBC iPlayer", 0xFF42A5F5L, ProviderRegistry.bbc, terrestrialLcn = 201, category = GuideCategory.KIDS),
        channel("cbeebies", 202, "CBeebies", "BBC iPlayer", 0xFFFFC44DL, ProviderRegistry.bbc, terrestrialLcn = 202, category = GuideCategory.KIDS),
        channel("bbc-news", 231, "BBC News", "BBC iPlayer", 0xFFB71C1CL, ProviderRegistry.bbc, terrestrialLcn = 231, category = GuideCategory.NEWS),
        channel("bbc-parliament", 232, "BBC Parliament", "BBC iPlayer", 0xFF6D4C9EL, ProviderRegistry.bbc, terrestrialLcn = 232, category = GuideCategory.NEWS),

        channel("sky-one", 101, "Sky One", "NOW Entertainment", 0xFF8E7DBEL, ProviderRegistry.now, nowChannelUri(4061)),
        channel("sky-atlantic", 102, "Sky Atlantic", "NOW Entertainment", 0xFF7768AAL, ProviderRegistry.now, nowChannelUri(4053)),
        channel("sky-witness", 103, "Sky Witness", "NOW Entertainment", 0xFF6750A4L, ProviderRegistry.now, nowChannelUri(4066)),
        channel("sky-comedy", 104, "Sky Comedy", "NOW Entertainment", 0xFFB05CC6L, ProviderRegistry.now, nowChannelUri(1143)),
        channel("sky-scifi", 105, "Sky Sci-Fi", "NOW Entertainment", 0xFF536DFEL, ProviderRegistry.now, nowChannelUri(4074)),
        channel("sky-documentaries", 106, "Sky Documentaries", "NOW Entertainment", 0xFF738290L, ProviderRegistry.now, nowChannelUri(1136), category = GuideCategory.DOCUMENTARY),
        channel("sky-crime", 107, "Sky Crime", "NOW Entertainment", 0xFFB83B5EL, ProviderRegistry.now, nowChannelUri(1212)),
        channel("sky-nature", 108, "Sky Nature", "NOW Entertainment", 0xFF44A87CL, ProviderRegistry.now, nowChannelUri(1165), category = GuideCategory.DOCUMENTARY),
        channel("sky-news", 109, "Sky News", "NOW Entertainment", 0xFFB51F2EL, ProviderRegistry.now, nowChannelUri(4050), category = GuideCategory.NEWS),
        channel("sky-history", 110, "Sky History", "NOW Entertainment", 0xFFC7924CL, ProviderRegistry.now, nowChannelUri(4086), category = GuideCategory.DOCUMENTARY),
        channel("u-gold", 111, "U&Gold", "NOW Entertainment", 0xFFF2B544L, ProviderRegistry.now, nowChannelUri(6513)),
        channel("u-alibi", 112, "U&Alibi", "NOW Entertainment", 0xFFB85ACAL, ProviderRegistry.now, nowChannelUri(3825)),
        channel("mtv", 113, "MTV", "NOW Entertainment", 0xFFE83283L, ProviderRegistry.now, nowChannelUri(3831)),
        channel("comedy-central", 114, "Comedy Central", "NOW Entertainment", 0xFF6E64C8L, ProviderRegistry.now, nowChannelUri(4056)),
        channel("sky-kids", 115, "Sky Kids", "NOW Kids", 0xFFFF8A40L, ProviderRegistry.now, nowChannelUri(1147), category = GuideCategory.KIDS),
        channel("boomerang", 130, "Boomerang", "NOW Kids", 0xFF4E9FDDL, ProviderRegistry.now, nowChannelUri(5609), category = GuideCategory.KIDS),
        channel("cartoon-network", 131, "Cartoon Network", "NOW Kids", 0xFF00A9E0L, ProviderRegistry.now, nowChannelUri(4077), category = GuideCategory.KIDS),
        channel("nickelodeon", 132, "Nickelodeon", "NOW Kids", 0xFFFF7A00L, ProviderRegistry.now, nowChannelUri(4069), category = GuideCategory.KIDS),
        channel("cartoonito", 133, "Cartoonito", "NOW Kids", 0xFF7B61A8L, ProviderRegistry.now, nowChannelUri(1371), category = GuideCategory.KIDS),
        channel("nick-jr", 134, "Nick Jr.", "NOW Kids", 0xFF55B848L, ProviderRegistry.now, nowChannelUri(4039), category = GuideCategory.KIDS),
        channel("nicktoons", 135, "Nicktoons", "NOW Kids", 0xFFE83E8CL, ProviderRegistry.now, nowChannelUri(1849), category = GuideCategory.KIDS),

        channel("discovery", 120, "Discovery", "discovery+", 0xFFFF6B35L, ProviderRegistry.discoveryPlus, category = GuideCategory.DOCUMENTARY),
        channel("animal-planet", 122, "Animal Planet", "discovery+", 0xFF4AA96CL, ProviderRegistry.discoveryPlus, category = GuideCategory.DOCUMENTARY),
        channel("investigation-discovery", 123, "Investigation Discovery", "discovery+", 0xFFBD3346L, ProviderRegistry.discoveryPlus),
        channel("discovery-science", 124, "Discovery Science", "discovery+", 0xFF367DB5L, ProviderRegistry.discoveryPlus, category = GuideCategory.DOCUMENTARY),
        channel("discovery-history", 125, "Discovery History", "discovery+", 0xFFA67842L, ProviderRegistry.discoveryPlus, category = GuideCategory.DOCUMENTARY),
        channel("discovery-turbo", 126, "Discovery Turbo", "discovery+", 0xFFD4552DL, ProviderRegistry.discoveryPlus, category = GuideCategory.DOCUMENTARY),

        channel("sky-cinema-premiere", 301, "Sky Cinema Premiere", "NOW Cinema", 0xFF5969D8L, ProviderRegistry.now, nowChannelUri(4021), category = GuideCategory.MOVIES),
        channel("sky-cinema-action", 302, "Sky Cinema Action", "NOW Cinema", 0xFFD14B45L, ProviderRegistry.now, nowChannelUri(4014), category = GuideCategory.MOVIES),
        channel("sky-cinema-drama", 303, "Sky Cinema Drama", "NOW Cinema", 0xFF9867C5L, ProviderRegistry.now, nowChannelUri(4016), category = GuideCategory.MOVIES),
        channel("sky-cinema-family", 304, "Sky Cinema Family", "NOW Cinema", 0xFF3AA6C1L, ProviderRegistry.now, nowChannelUri(4018), category = GuideCategory.MOVIES),
        channel("sky-cinema-greats", 305, "Sky Cinema Greats", "NOW Cinema", 0xFFD0A33EL, ProviderRegistry.now, nowChannelUri(4015), category = GuideCategory.MOVIES),
        channel("sky-cinema-thriller", 306, "Sky Cinema Thriller", "NOW Cinema", 0xFF7854A8L, ProviderRegistry.now, nowChannelUri(4062), category = GuideCategory.MOVIES),

        channel("sky-sports-main-event", 401, "Sky Sports Main Event", "NOW Sports", 0xFF2B78E4L, ProviderRegistry.now, nowChannelUri(4002), category = GuideCategory.SPORT),
        channel("sky-sports-premier-league", 402, "Sky Sports Premier League", "NOW Sports", 0xFF33A852L, ProviderRegistry.now, nowChannelUri(4011), category = GuideCategory.SPORT),
        channel("sky-sports-football", 403, "Sky Sports Football", "NOW Sports", 0xFF23A36DL, ProviderRegistry.now, nowChannelUri(3942), category = GuideCategory.SPORT),
        channel("sky-sports-cricket", 404, "Sky Sports Cricket", "NOW Sports", 0xFFE65B3EL, ProviderRegistry.now, nowChannelUri(4081), category = GuideCategory.SPORT),
        channel("sky-sports-f1", 405, "Sky Sports F1", "NOW Sports", 0xFFD22F2FL, ProviderRegistry.now, nowChannelUri(3835), category = GuideCategory.SPORT),
        channel("sky-sports-golf", 406, "Sky Sports Golf", "NOW Sports", 0xFF23966AL, ProviderRegistry.now, nowChannelUri(4026), category = GuideCategory.SPORT),
        channel("sky-sports-action", 407, "Sky Sports Action", "NOW Sports", 0xFFB84FCEL, ProviderRegistry.now, nowChannelUri(4024), category = GuideCategory.SPORT),
        channel("sky-sports-news", 408, "Sky Sports News", "NOW Sports", 0xFF2D7EB9L, ProviderRegistry.now, nowChannelUri(4036), category = GuideCategory.SPORT),

        // HBO Max UK documents these four linear channels. Direct channel links have
        // not been verified, so the safe behaviour is an explicit HBO Max app handoff.
        channel("tnt-sports-1", 420, "TNT Sports 1", "HBO Max · TNT Sports", 0xFFFF4F73L, ProviderRegistry.hboMax, category = GuideCategory.SPORT),
        channel("tnt-sports-2", 421, "TNT Sports 2", "HBO Max · TNT Sports", 0xFF7E6BFFL, ProviderRegistry.hboMax, category = GuideCategory.SPORT),
        channel("tnt-sports-3", 422, "TNT Sports 3", "HBO Max · TNT Sports", 0xFF16B7AAL, ProviderRegistry.hboMax, category = GuideCategory.SPORT),
        channel("tnt-sports-4", 423, "TNT Sports 4", "HBO Max · TNT Sports", 0xFFFFA538L, ProviderRegistry.hboMax, category = GuideCategory.SPORT),

        // Pluto TV UK: curated Free Ad-Supported Television rows. The IDs come
        // from Pluto's UK EPG and are launched through the installed official
        // Android TV EntryPoint; no stream manifest is embedded in this app.
        channel("pluto-cops", 601, "COPS", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("661cf62d24e1d000087dae0d"), xmlTvId = "Cops.us@SD"),
        channel("pluto-5-cops", 602, "5 Cops", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5d2c571faeb3e2738ae27933"), xmlTvId = "5Cops.us@UK"),
        channel("pluto-ghost-hunters", 603, "Ghost Hunters", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5f27bbe4779de70007a6d1c1"), category = GuideCategory.DOCUMENTARY, xmlTvId = "GhostHunters.us@UK"),
        channel("pluto-deadly-women", 604, "Deadly Women", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5ca1df0d50be2571e393ad31"), xmlTvId = "DeadlyWomen.us@UK"),
        channel("pluto-hells-kitchen", 605, "Hell's Kitchen", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5e6f38792075160007d85823"), xmlTvId = "HellsKitchen.us@UK"),
        channel("pluto-homes-under-hammer", 606, "Homes Under the Hammer", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5c5c2e9d8002db3c3e0b1c72"), category = GuideCategory.DOCUMENTARY, xmlTvId = "HomesUnderHammer.us@UK"),
        channel("pluto-andromeda", 607, "Andromeda", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5e8db96bccae160007c71eec"), xmlTvId = "Andromeda.us@UK"),
        channel("pluto-avatar", 608, "Avatar", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("656df599c0fc8800089c75ab"), category = GuideCategory.KIDS, xmlTvId = "Avatar.us@UK"),
        channel("pluto-duck-dynasty", 609, "Duck Dynasty", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5f6b54b9e67cf60007d4cef1"), category = GuideCategory.DOCUMENTARY, xmlTvId = "DuckDynasty.us@UK"),
        channel("pluto-euronews", 610, "Euronews", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5ca1da6c593a5d78f0e7edce"), category = GuideCategory.NEWS, xmlTvId = "EuronewsEnglish.fr@SD"),
        channel("pluto-fifa-plus", 611, "FIFA+", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("63d91d2560bc8f0008a225e4"), category = GuideCategory.SPORT, xmlTvId = "FIFAPlus.uk@English"),
        // Verified against the installed UK Android TV guide. These are the
        // real channel identities, not programme titles shown in the guide.
        channel("pluto-5-trucking-hell", 612, "5 Trucking Hell", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("656df2849d5ac400083be647"), category = GuideCategory.DOCUMENTARY, xmlTvId = "5TruckingHell.uk"),
        channel("pluto-dog-bounty-hunter", 613, "Dog The Bounty Hunter", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5f6b535a278bfe000799484a"), category = GuideCategory.DOCUMENTARY, xmlTvId = "DogtheBountyHunter.us@UK"),
        channel("pluto-bondi-rescue", 614, "Bondi Rescue", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("660be37e7bcd860008793a6a"), category = GuideCategory.DOCUMENTARY, xmlTvId = "BondiRescue.uk"),
        channel("pluto-wicked-tuna", 615, "Wicked Tuna", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("63693fa2bfa0690007ebc880"), category = GuideCategory.DOCUMENTARY, xmlTvId = "WickedTuna.uk"),
        channel("pluto-5-emergency-rescue", 616, "5 Emergency Rescue", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("63c1384e829c850007922ca4"), category = GuideCategory.DOCUMENTARY, xmlTvId = "5EmergencyRescue.uk"),
        channel("pluto-movies", 617, "Pluto TV Movies", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5ad8d3a31b95267e225e4e09"), category = GuideCategory.MOVIES, xmlTvId = "PlutoTVMovies.de@UK"),
        channel("pluto-action", 618, "Pluto TV Action", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5dbfeb961b411c00090b52b3"), category = GuideCategory.MOVIES, xmlTvId = "PlutoTVAction.de@UK"),
        channel("pluto-comedy-movies", 619, "Pluto TV Comedy Movies", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5c363c2411c5ca053f198f97"), category = GuideCategory.MOVIES, xmlTvId = "PlutoTVComedyMovies.de@UK"),
        channel("pluto-dazn-ringside", 620, "DAZN Ringside", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("65b14aae0cb1a100087a216e"), category = GuideCategory.SPORT, xmlTvId = "DAZNRingside.uk"),
        channel("pluto-rugby-pass", 621, "Rugby Pass TV", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("66f137cb483d460008ece053"), category = GuideCategory.SPORT, xmlTvId = "RugbyPassTV.uk@SD"),
        channel("pluto-tennis", 622, "Tennis Channel", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("6627621d56fc840008e47c90"), category = GuideCategory.SPORT, xmlTvId = "TennisChannel.us@SD"),
        channel("pluto-crime", 623, "Pluto TV Crime", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5d767790d0438aceb41d03ae"), xmlTvId = "PlutoTVCrime.de@UK"),
        channel("pluto-crime-drama", 624, "Pluto TV Crime Drama", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("65a6a7a00c7ff50008cba07b"), xmlTvId = "PlutoTVCrimeDrama.de@UK"),
        channel("pluto-sitcoms", 625, "Pluto TV Sitcoms", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("62f53dc6ee9a0400071dc22b"), xmlTvId = "PlutoTVSitcoms.de@UK"),
        channel("pluto-u-laughs", 626, "U&Laughs", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("64c0d6f34096290008132d6b"), xmlTvId = "ULaughs.uk"),
        channel("pluto-kids", 627, "Pluto TV Kids", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("5ad8d54be738977e2c310940"), category = GuideCategory.KIDS, xmlTvId = "PlutoTVKids.de@UK"),
        channel("pluto-pokemon", 628, "Pokémon", "Pluto TV", 0xFF70C8FFL, ProviderRegistry.pluto, plutoChannelUri("6683cd71a1d7ad000866ec6a"), category = GuideCategory.KIDS, xmlTvId = "PlutoTVPokemon.us@UK"),
    )

    val byId = channels.associateBy { it.id }
    val byXmlTvId = channels.associateBy { it.xmlTvId }
}
