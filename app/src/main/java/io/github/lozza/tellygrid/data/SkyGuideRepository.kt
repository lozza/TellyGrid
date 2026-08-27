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

    fun load(): GuideLoadResult {
        if (BuildConfig.EPG_URL.isNotBlank()) {
            runCatching { download(BuildConfig.EPG_URL) }.getOrNull()?.let { bytes ->
                val channels = bytes.inputStream().use(parser::parse)
                if (channels.isNotEmpty()) {
                    cacheFile.writeBytes(bytes)
                    return GuideLoadResult(channels, "Live Sky listings")
                }
            }
        }

        if (cacheFile.exists()) {
            runCatching { cacheFile.inputStream().use(parser::parse) }.getOrNull()?.let { channels ->
                if (channels.isNotEmpty()) return GuideLoadResult(channels, "Saved Sky listings — refresh unavailable")
            }
        }

        val bundled = context.assets.open("sky-guide.xml").use(parser::parse)
        check(bundled.isNotEmpty()) { "The bundled Sky guide contains no current programmes" }
        return GuideLoadResult(bundled, "Sky listings bundled with this build")
    }

    private fun download(feedUrl: String): ByteArray {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/xml, text/xml")
            check(connection.responseCode in 200..299) { "EPG server returned ${connection.responseCode}" }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
