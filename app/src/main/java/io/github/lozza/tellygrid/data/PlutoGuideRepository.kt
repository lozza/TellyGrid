package io.github.lozza.tellygrid.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Supplements the general XMLTV feed with the official Pluto programme API.
 * Pluto's Android TV guide is region-specific and does not register its rows
 * with Android's Live Channels provider, so this is the source used by the
 * installed Pluto app itself for the supported UK channel identities.
 */
class PlutoGuideRepository(private val context: Context) {
    private val cacheDirectory = File(context.cacheDir, "pluto-guide")

    fun enrich(channels: List<GuideChannel>, now: Instant = Instant.now()): PlutoGuideLoadResult {
        val schedules = mutableMapOf<String, List<Programme>>()
        var refreshed = 0
        val requests = channels.mapNotNull request@{ channel ->
            val handoff = channel.playback as? PlaybackTarget.ProviderHandoff ?: return@request null
            if (handoff.provider.id != ProviderId.PLUTO_TV) return@request null
            val plutoId = handoff.channelUri?.substringAfterLast('/')?.takeIf { it.matches(Regex("[a-f0-9]{24}")) }
                ?: return@request null
            PlutoRequest(channel.id, plutoId)
        }

        if (requests.isNotEmpty()) {
            val executor = Executors.newFixedThreadPool(minOf(4, requests.size))
            try {
                executor.invokeAll(
                    requests.map { request -> Callable { fetch(request, now) } },
                    6,
                    TimeUnit.SECONDS,
                ).forEach { future ->
                    runCatching { future.get() }.getOrNull()?.let { result ->
                        if (result.programmes.isNotEmpty()) schedules[result.channelId] = result.programmes
                        if (result.refreshed) refreshed++
                    }
                }
            } finally {
                executor.shutdownNow()
            }
        }

        return PlutoGuideLoadResult(
            channels = channels.map { channel ->
                schedules[channel.id]?.let { programmes -> channel.copy(programmes = programmes) } ?: channel
            },
            refreshedChannelCount = refreshed,
        )
    }

    private fun fetch(request: PlutoRequest, now: Instant): PlutoSchedule {
        val cacheFile = File(cacheDirectory, "${request.plutoId}.json")
        // Reuse a recent schedule on launch; the guide refresh path will fetch
        // again after the short TTL instead of blocking every startup.
        val cacheIsFresh = cacheFile.exists() &&
            now.toEpochMilli() - cacheFile.lastModified() < 30 * 60 * 1000L
        val downloaded = if (cacheIsFresh) null else runCatching { download(request.plutoId, now) }.getOrNull()
        if (downloaded != null) {
            cacheDirectory.mkdirs()
            cacheFile.writeBytes(downloaded)
        }
        val programmes = (downloaded ?: cacheFile.takeIf(File::exists)?.readBytes())
            ?.let { parse(it, request.channelId, now) }
            .orEmpty()
        return PlutoSchedule(request.channelId, programmes, downloaded != null)
    }

    private fun download(channelId: String, now: Instant): ByteArray {
        val start = now.minusSeconds(6 * 60 * 60).toString()
        val stop = now.plusSeconds(7 * 24 * 60 * 60).toString()
        val connection = URL("https://api.pluto.tv/v2/channels/$channelId?start=$start&stop=$stop")
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode in 200..299) { "Pluto guide returned ${connection.responseCode}" }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(bytes: ByteArray, channelId: String, now: Instant): List<Programme> {
        val timelines = JSONObject(bytes.toString(Charsets.UTF_8)).optJSONArray("timelines") ?: return emptyList()
        return buildList {
            for (index in 0 until timelines.length()) {
                val item = timelines.optJSONObject(index) ?: continue
                val startsAt = item.optString("start").toInstantOrNull() ?: continue
                val endsAt = item.optString("stop").toInstantOrNull() ?: continue
                if (!endsAt.isAfter(now)) continue

                val episode = item.optJSONObject("episode")
                val seriesTitle = item.optString("title").trim()
                val episodeTitle = episode?.optString("name").orEmpty().trim()
                val title = listOf(seriesTitle, episodeTitle)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(": ")
                    .ifBlank { "Programme information unavailable" }
                val artwork = episode?.optJSONObject("series")?.optJSONObject("tile")?.optString("path")
                    ?.takeIf { it.startsWith("https://") }

                add(
                    Programme(
                        id = "$channelId-${startsAt.epochSecond}",
                        title = title,
                        synopsis = episode?.optString("description").orEmpty(),
                        startsAt = startsAt,
                        endsAt = endsAt,
                        imageUri = artwork,
                    ),
                )
            }
        }.distinctBy(Programme::id).sortedBy(Programme::startsAt)
    }

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    private data class PlutoRequest(val channelId: String, val plutoId: String)
    private data class PlutoSchedule(
        val channelId: String,
        val programmes: List<Programme>,
        val refreshed: Boolean,
    )
}

data class PlutoGuideLoadResult(
    val channels: List<GuideChannel>,
    val refreshedChannelCount: Int,
)
