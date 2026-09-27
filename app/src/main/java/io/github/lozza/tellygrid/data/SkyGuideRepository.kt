package io.github.lozza.tellygrid.data

import android.content.Context
import io.github.lozza.tellygrid.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class GuideLoadResult(
    val channels: List<GuideChannel>,
    val status: String,
)

class SkyGuideRepository(private val context: Context) {
    private val parser = XmlTvGuideParser()
    private val cacheFile = File(context.filesDir, "sky-guide.xml")
    private val demoFallback by lazy { SampleGuideRepository().channels() }
    private val plutoGuide = PlutoGuideRepository(context)

    fun load(): GuideLoadResult {
        val bundled by lazy { context.assets.open("sky-guide.xml").use(parser::parse) }
        val now = System.currentTimeMillis()
        val cachedFresh = cacheFile.exists() && now - cacheFile.lastModified() < 30 * 60 * 1000L
        val cachedResult = if (cachedFresh) {
            runCatching { cacheFile.inputStream().use(parser::parse) }.getOrNull()
                ?.takeIf(::hasUsableSkyListings)
                ?.let { channels ->
                    GuideLoadResult(
                        mergeGuideSchedules(channels, bundled, demoFallback),
                        "Saved Sky listings",
                    )
                }
        } else null
        val base = if (BuildConfig.EPG_URL.isNotBlank()) {
            cachedResult ?: runCatching { download(BuildConfig.EPG_URL) }.getOrNull()?.let { bytes ->
                val channels = bytes.inputStream().use(parser::parse)
                if (hasUsableSkyListings(channels)) {
                    cacheFile.writeBytes(bytes)
                    GuideLoadResult(
                        mergeGuideSchedules(channels, bundled, demoFallback),
                        "Live Sky listings",
                    )
                } else {
                    null
                }
            }
        } else {
            null
        } ?: if (cacheFile.exists()) {
            runCatching { cacheFile.inputStream().use(parser::parse) }.getOrNull()?.let { channels ->
                if (hasUsableSkyListings(channels)) {
                    GuideLoadResult(
                        mergeGuideSchedules(channels, bundled, demoFallback),
                        "Saved Sky listings — refresh unavailable",
                    )
                } else {
                    null
                }
            }
        } else {
            null
        } ?: if (hasUsableSkyListings(bundled)) {
            GuideLoadResult(
                mergeGuideSchedules(bundled, emptyList(), demoFallback),
                "Sky listings bundled with this build",
            )
        } else {
            // An APK can outlive its two-day snapshot. Keep the guide usable with
            // relative demo rows rather than rendering a full page of dead
            // "Listings unavailable" placeholders.
            GuideLoadResult(
            SampleGuideRepository().channels(),
            "Sky listings unavailable — showing demo data",
            )
        }

        val pluto = plutoGuide.enrich(base.channels)
        return base.copy(
            channels = pluto.channels,
            status = if (pluto.refreshedChannelCount > 0) {
                "${base.status} · Pluto TV listings updated"
            } else {
                base.status
            },
        )
    }

    private fun download(feedUrl: String): ByteArray {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/xml, text/xml")
            check(connection.responseCode in 200..299) { "EPG server returned ${connection.responseCode}" }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}

internal fun hasUsableListings(channels: List<GuideChannel>): Boolean =
    channels.any { channel ->
        channel.programmes.any { programme ->
            !programme.id.endsWith("-listings-unavailable")
        }
    }

/**
 * A feed is only a valid Sky guide when it contains current data for the
 * main terrestrial rows. Pluto/FAST rows can refresh independently and must
 * not make a mostly-empty Sky feed look healthy.
 */
internal fun hasUsableSkyListings(channels: List<GuideChannel>): Boolean {
    val coreIds = setOf("bbc-one", "bbc-two", "itv1", "channel-4", "five")
    val core = channels.filter { it.id in coreIds }
    return core.size >= 3 && core.count { channel ->
        channel.programmes.any { !it.id.endsWith("-listings-unavailable") }
    } >= 3
}

/** Keeps a fresh hosted feed authoritative while filling newly-added rows from the APK. */
internal fun mergeGuideSchedules(
    primary: List<GuideChannel>,
    bundledFallback: List<GuideChannel>,
    demoFallback: List<GuideChannel> = emptyList(),
): List<GuideChannel> {
    val bundledById = bundledFallback.associateBy { it.id }
    val demoById = demoFallback.associateBy { it.id }
    return primary.map { channel ->
        val hasListings = channel.programmes.any { !it.id.endsWith("-listings-unavailable") }
        val bundled = bundledById[channel.id]
        val bundledHasListings = bundled?.programmes?.any { !it.id.endsWith("-listings-unavailable") } == true
        val demo = demoById[channel.id]
        val demoHasListings = demo?.programmes?.any { !it.id.endsWith("-listings-unavailable") } == true
        when {
            hasListings -> channel
            bundledHasListings -> channel.copy(programmes = bundled!!.programmes)
            demoHasListings -> channel.copy(programmes = demo!!.programmes)
            else -> channel
        }
    }
}
