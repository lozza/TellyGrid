package io.github.lozza.tellygrid.data

import android.content.Context
import android.media.tv.TvContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * TiviMate support. TiviMate has no public channel API, so TellyGrid only uses what
 * TiviMate itself publishes to Android TV (its home screen channel rows). Each row
 * carries TiviMate's own intent, which opens that channel directly in TiviMate.
 */
object TiviMate {
    const val LABEL = "TiviMate"
    val packageNames = setOf("ar.tvplayer.tv")

    fun owns(programme: TvPublishedProgramme): Boolean = programme.packageName in packageNames

    /** TiviMate's published rows, for its own Home recommendation row. */
    fun recommendations(published: TvPublishedRecommendations): List<TvPublishedProgramme> =
        published.preview.filter(::owns)

    /**
     * Guide channels built from the channels TiviMate publishes. On-demand items
     * (films, episodes) stay on Home; only live channels go in the Guide.
     */
    fun guideChannels(published: TvPublishedRecommendations, now: Instant = Instant.now()): List<GuideChannel> =
        (published.preview + published.watchNext)
            .filter { owns(it) && it.isChannel() }
            .distinctBy { it.title.lowercase() }
            .sortedBy { it.title.lowercase() }
            .mapIndexed { index, item ->
                val start = item.startsAtMillis?.let(Instant::ofEpochMilli)
                val end = item.endsAtMillis?.let(Instant::ofEpochMilli)
                val hasTimes = start != null && end != null && end.isAfter(now)
                GuideChannel(
                    id = "tivimate:${item.title.lowercase()}",
                    number = CHANNEL_NUMBER_START + index,
                    name = item.title,
                    providerLabel = LABEL,
                    accentArgb = 0xFF7C4DFF,
                    logoUri = item.artworkUri,
                    category = GuideCategory.ENTERTAINMENT,
                    playback = PlaybackTarget.PublishedChannel(item, LABEL),
                    programmes = listOf(
                        Programme(
                            id = "tivimate:${item.title.lowercase()}:live",
                            title = item.description?.takeIf { it.length <= 80 } ?: "Live on $LABEL",
                            synopsis = item.description.orEmpty(),
                            // TiviMate rows rarely carry listings, so one block keeps the
                            // channel selectable across the whole guide window.
                            startsAt = if (hasTimes) start!! else now.minus(Duration.ofDays(1)),
                            endsAt = if (hasTimes) end!! else now.plus(Duration.ofDays(8)),
                            imageUri = item.artworkUri,
                        ),
                    ),
                )
            }

    private fun TvPublishedProgramme.isChannel(): Boolean =
        live || type == null || type == TvContract.PreviewPrograms.TYPE_CHANNEL

    private const val CHANNEL_NUMBER_START = 2000
}

/**
 * TiviMate channels opened from TellyGrid, newest first. Shown in Watch Next even when
 * TiviMate publishes no Watch Next rows of its own.
 */
class TiviMateRecents(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<TvPublishedProgramme> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            TvPublishedProgramme(
                title = item.optString("title").ifBlank { return@mapNotNull null },
                packageName = item.optString("package").ifBlank { return@mapNotNull null },
                intentUri = item.optString("intent").ifBlank { return@mapNotNull null },
                artworkUri = item.optString("art").ifBlank { null },
                lastEngagedAtMillis = item.optLong("at").takeIf { it > 0 },
                description = item.optString("description").ifBlank { null },
            )
        }
    }.getOrDefault(emptyList())

    /** Records [programme] as just watched and returns it with that time. */
    fun record(programme: TvPublishedProgramme, nowMillis: Long = System.currentTimeMillis()): TvPublishedProgramme {
        val watched = programme.copy(lastEngagedAtMillis = nowMillis)
        val updated = (listOf(watched) + load())
            .distinctBy { it.packageName to it.intentUri }
            .take(MAX_ITEMS)
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject()
                    .put("title", item.title)
                    .put("package", item.packageName)
                    .put("intent", item.intentUri)
                    .put("art", item.artworkUri.orEmpty())
                    .put("at", item.lastEngagedAtMillis ?: 0L)
                    .put("description", item.description.orEmpty()),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
        return watched
    }

    private companion object {
        const val PREFS = "tivimate"
        const val KEY = "recent_channels"
        const val MAX_ITEMS = 12
    }
}

/** Adds TellyGrid's own watch history to Watch Next, most recent first, without duplicates. */
fun TvPublishedRecommendations.withLocalWatchNext(local: List<TvPublishedProgramme>): TvPublishedRecommendations =
    if (local.isEmpty()) this else copy(
        watchNext = (watchNext + local)
            .sortedByDescending { it.lastEngagedAtMillis ?: Long.MIN_VALUE }
            .distinctBy { it.packageName to it.intentUri },
    )
