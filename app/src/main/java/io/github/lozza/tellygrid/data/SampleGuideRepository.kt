package io.github.lozza.tellygrid.data

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Replace with a licensed EPG/API repository before shipping. */
class SampleGuideRepository(private val clock: Clock = Clock.systemUTC()) {
    fun channels(): List<GuideChannel> {
        val hour = Instant.now(clock).truncatedTo(ChronoUnit.HOURS)
        return listOf(
            channel("bbc-one", 1, "BBC One", "BBC iPlayer", 0xFFEF476FL, ProviderRegistry.bbc, hour, "Morning Live", "BBC News at One"),
            channel("bbc-two", 2, "BBC Two", "BBC iPlayer", 0xFFF59E0BL, ProviderRegistry.bbc, hour, "Politics Live", "Coast"),
            channel("itv1", 3, "ITV1", "ITVX", 0xFF06D6A0L, ProviderRegistry.itvx, hour, "This Morning", "ITV Lunchtime News"),
            channel("channel-4", 4, "Channel 4", "Channel 4", 0xFF4CC9F0L, ProviderRegistry.channel4, hour, "A Place in the Sun", "Channel 4 News"),
            channel("five", 5, "5", "5", 0xFFB5179EL, ProviderRegistry.five, hour, "Bargain Loving Brits", "5 News"),
            channel("sky-atlantic", 106, "Sky Atlantic", "NOW membership", 0xFF8E7DBEL, ProviderRegistry.now, hour, "Featured drama", "New episode"),
            channel("discovery", 125, "Discovery", "discovery+ plan", 0xFFFF6B35L, ProviderRegistry.discoveryPlus, hour, "How It's Made", "Expedition Unknown"),
        )
    }

    private fun channel(
        id: String,
        number: Int,
        name: String,
        providerLabel: String,
        accent: Long,
        provider: ProviderApp,
        hour: Instant,
        nowTitle: String,
        nextTitle: String,
    ): GuideChannel {
        val firstStart = hour.minus(30, ChronoUnit.MINUTES)
        val firstEnd = firstStart.plus(Duration.ofMinutes(60))
        return GuideChannel(
            id = id,
            number = number,
            name = name,
            providerLabel = providerLabel,
            accentArgb = accent,
            playback = PlaybackTarget.ProviderHandoff(
                provider = provider,
                terrestrialLcn = number.takeIf { it in 1..5 },
            ),
            programmes = listOf(
                Programme("$id-now", nowTitle, "Illustrative guide data — connect a licensed EPG for production.", firstStart, firstEnd),
                Programme("$id-next", nextTitle, "Up next", firstEnd, firstEnd.plus(Duration.ofMinutes(60))),
            ),
        )
    }
}
