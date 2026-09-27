package io.github.lozza.tellygrid.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** An iPlayer episode as listed by BBC's own catalogue, keyed by its BBC episode id. */
data class BbcIplayerEpisode(
    val id: String,
    val title: String,
    val subtitle: String?,
    val artworkUri: String?,
)

/**
 * Loads BBC iPlayer's "Most popular" episodes from the public iBL catalogue
 * that bbc.co.uk/iplayer itself uses. Every card carries a BBC-issued episode
 * id, so the Home shelf never guesses an id from a title.
 */
class BbcIplayerCatalogueRepository(private val context: Context) {
    private val cacheFile = File(context.filesDir, "bbc-iplayer-popular.json")

    fun load(): List<BbcIplayerEpisode> {
        val cacheFresh = cacheFile.exists() &&
            System.currentTimeMillis() - cacheFile.lastModified() < CACHE_MILLIS
        if (cacheFresh) {
            readCache()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val downloaded = runCatching { download(POPULAR_URL) }.getOrNull()
        val episodes = downloaded?.let { runCatching { parseBbcIplayerEpisodes(it) }.getOrNull() }
        if (downloaded != null && !episodes.isNullOrEmpty()) {
            runCatching { cacheFile.writeText(downloaded) }
            return episodes
        }
        // An older list still opens real episodes; BBC shows its own message if one has expired.
        return readCache().orEmpty()
    }

    private fun readCache(): List<BbcIplayerEpisode>? =
        runCatching { parseBbcIplayerEpisodes(cacheFile.readText()) }.getOrNull()

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "TellyGrid/0.6")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("iPlayer catalogue returned ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CACHE_MILLIS = 60 * 60 * 1000L
        const val POPULAR_URL =
            "https://ibl.api.bbc.co.uk/ibl/v1/groups/popular/episodes?rights=tv&availability=available&page=1&per_page=20"
    }
}

private val bbcEpisodeId = Regex("^[a-z0-9]{8,15}$")

/** Parses an iBL group-episodes response. Unknown or malformed entries are skipped. */
fun parseBbcIplayerEpisodes(json: String, imageRecipe: String = "480x270"): List<BbcIplayerEpisode> {
    val root = JSONObject(json)
    val elements = root.optJSONObject("group_episodes")?.optJSONArray("elements")
        ?: root.optJSONArray("elements")
        ?: return emptyList()
    return buildList {
        for (index in 0 until elements.length()) {
            val element = elements.optJSONObject(index) ?: continue
            val type = element.text("type").orEmpty()
            if (type.isNotEmpty() && type != "episode") continue
            val id = element.text("id").orEmpty()
            val title = element.text("title").orEmpty()
            if (!bbcEpisodeId.matches(id) || title.isEmpty()) continue
            val artwork = element.optJSONObject("images")
                ?.text("standard")
                ?.takeIf { it.startsWith("https://ichef.bbci.co.uk/") }
                ?.replace("{recipe}", imageRecipe)
            add(
                BbcIplayerEpisode(
                    id = id,
                    title = title,
                    subtitle = element.text("subtitle"),
                    artworkUri = artwork,
                ),
            )
        }
    }.distinctBy { it.id }
}

/** org.json turns JSON null into the string "null"; treat it, and blanks, as absent. */
private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)
