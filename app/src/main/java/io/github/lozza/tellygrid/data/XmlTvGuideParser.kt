package io.github.lozza.tellygrid.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class XmlTvGuideParser {
    private val xmlTvTime = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z", Locale.ROOT)

    fun parse(input: InputStream, now: Instant = Instant.now()): List<GuideChannel> {
        val programmes = mutableMapOf<String, MutableList<Programme>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, "UTF-8")
        }

        var event = parser.eventType
        var activeChannel: String? = null
        var activeStart: Instant? = null
        var activeStop: Instant? = null
        var title = "Programme information unavailable"
        var description = ""
        var imageUri: String? = null
        var episodeLabel: String? = null
        var contentUri: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        activeChannel = parser.getAttributeValue(null, "channel")
                        activeStart = parseTime(parser.getAttributeValue(null, "start"))
                        activeStop = parseTime(parser.getAttributeValue(null, "stop"))
                        title = "Programme information unavailable"
                        description = ""
                        imageUri = null
                        episodeLabel = null
                        contentUri = null
                    }
                    "title" -> if (activeChannel != null) title = parser.nextText().trim()
                    "desc" -> if (activeChannel != null) description = parser.nextText().trim()
                    "icon" -> if (activeChannel != null && imageUri == null) {
                        imageUri = parser.getAttributeValue(null, "src")?.trim()?.takeIf { it.startsWith("https://") }
                    }
                    "image" -> if (activeChannel != null && imageUri == null) {
                        imageUri = parser.nextText().trim().takeIf { it.startsWith("https://") }
                    }
                    "episode-num" -> if (activeChannel != null) {
                        val system = parser.getAttributeValue(null, "system")
                        val value = parser.nextText().trim()
                        when (system) {
                            "onscreen" -> episodeLabel = value.takeIf { it.isNotBlank() }
                            "discoveryplus" -> contentUri = value
                        }
                    }
                    "url" -> if (activeChannel != null) contentUri = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme") {
                    val channelId = activeChannel
                    val startsAt = activeStart
                    val endsAt = activeStop
                    if (channelId != null && startsAt != null && endsAt != null && endsAt.isAfter(now)) {
                        programmes.getOrPut(channelId) { mutableListOf() }.add(
                            Programme(
                                id = "$channelId-${startsAt.epochSecond}",
                                title = title.ifBlank { "Programme information unavailable" },
                                synopsis = description,
                                startsAt = startsAt,
                                endsAt = endsAt,
                                imageUri = imageUri,
                                episodeLabel = episodeLabel,
                                contentUri = DiscoveryProgrammeLink.verifiedOrNull(channelId, contentUri)
                                    ?: BbcIplayerProgrammeLink.verifiedOrNull(contentUri.takeIf {
                                        ChannelCatalog.byXmlTvId[channelId]?.provider?.id == ProviderId.BBC_IPLAYER
                                    }),
                            ),
                        )
                    }
                    activeChannel = null
                }
            }
            event = parser.next()
        }

        return ChannelCatalog.channels.map { catalog ->
            val listedSchedule = programmes[catalog.xmlTvId]
                ?.distinctBy { it.id }
                ?.sortedBy { it.startsAt }
                // Keep enough of the feed to cover the complete seven-day
                // date strip. Short EPG slices made later dates look empty
                // even though the provider had listings for them.
                ?.take(96)
                .orEmpty()
            val schedule = listedSchedule.ifEmpty {
                listOf(
                    Programme(
                        id = "${catalog.xmlTvId}-listings-unavailable",
                        title = "Listings unavailable",
                        synopsis = "This channel is available through ${catalog.providerLabel}.",
                        startsAt = now,
                        endsAt = now.plusSeconds(24 * 60 * 60),
                    ),
                )
            }

            GuideChannel(
                id = catalog.id,
                number = catalog.number,
                name = catalog.displayName,
                providerLabel = catalog.providerLabel,
                accentArgb = catalog.accentArgb,
                logoUri = catalog.logoUri,
                category = catalog.category,
                playback = PlaybackTarget.ProviderHandoff(
                    provider = catalog.provider,
                    channelId = catalog.id,
                    channelUri = catalog.channelUri,
                    terrestrialLcn = catalog.terrestrialLcn,
                ),
                programmes = schedule,
            )
        }
    }

    private fun parseTime(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val match = Regex("^(\\d{14})\\s*([+-]\\d{4})").find(value.trim()) ?: return null
        return runCatching {
            OffsetDateTime.parse("${match.groupValues[1]} ${match.groupValues[2]}", xmlTvTime).toInstant()
        }.getOrNull()
    }
}
