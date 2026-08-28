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

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        activeChannel = parser.getAttributeValue(null, "channel")
                        activeStart = parseTime(parser.getAttributeValue(null, "start"))
                        activeStop = parseTime(parser.getAttributeValue(null, "stop"))
                        title = "Programme information unavailable"
                        description = ""
                    }
                    "title" -> if (activeChannel != null) title = parser.nextText().trim()
                    "desc" -> if (activeChannel != null) description = parser.nextText().trim()
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
                            ),
                        )
                    }
                    activeChannel = null
                }
            }
            event = parser.next()
        }

        return ChannelCatalog.channels.mapNotNull { catalog ->
            val schedule = programmes[catalog.xmlTvId]
                ?.distinctBy { it.id }
                ?.sortedBy { it.startsAt }
                ?.take(12)
                .orEmpty()
            if (schedule.isEmpty()) return@mapNotNull null

            GuideChannel(
                id = catalog.xmlTvId,
                number = catalog.number,
                name = catalog.displayName,
                providerLabel = catalog.providerLabel,
                accentArgb = catalog.accentArgb,
                playback = PlaybackTarget.ProviderHandoff(
                    provider = catalog.provider,
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
