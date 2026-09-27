package io.github.lozza.tellygrid.data

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract

/** Programme metadata and launch intent published by an installed Android TV app. */
data class TvPublishedProgramme(
    val title: String,
    val packageName: String,
    val intentUri: String,
    val artworkUri: String?,
    /** When the viewer last engaged with this title, as reported by the app; null if unknown. */
    val lastEngagedAtMillis: Long? = null,
    val description: String? = null,
)

data class TvPublishedRecommendations(
    val preview: List<TvPublishedProgramme> = emptyList(),
    val watchNext: List<TvPublishedProgramme> = emptyList(),
    /** False until the first read of the TV's rows has finished (or was not permitted). */
    val loaded: Boolean = false,
)

class TvPublishedRecommendationsRepository(private val context: Context) {
    fun load(): TvPublishedRecommendations = TvPublishedRecommendations(
        preview = read(TvContract.PreviewPrograms.CONTENT_URI),
        // The provider returns rows in insertion order, so without sorting the oldest
        // entries always filled the shelf. Most recently watched first, like Android TV Home.
        watchNext = read(TvContract.WatchNextPrograms.CONTENT_URI)
            .sortedByDescending { it.lastEngagedAtMillis ?: Long.MIN_VALUE },
        loaded = true,
    )

    private fun read(uri: android.net.Uri): List<TvPublishedProgramme> = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < 1_000) {
                    if (cursor.value("browsable") == "0" || cursor.value("searchable") == "0") continue
                    val title = cursor.value("title")?.trim().orEmpty()
                    val packageName = cursor.value("package_name")?.trim().orEmpty()
                    val intentUri = cursor.value("intent_uri")?.trim().orEmpty()
                    if (title.isBlank() || packageName.isBlank() || intentUri.isBlank()) continue
                    add(
                        TvPublishedProgramme(
                            title = title,
                            packageName = packageName,
                            intentUri = intentUri,
                            artworkUri = cursor.value("poster_art_uri")?.takeIf(String::isNotBlank)
                                ?: cursor.value("thumbnail_uri")?.takeIf(String::isNotBlank),
                            description = cursor.value("short_description")?.trim()?.takeIf(String::isNotBlank),
                            lastEngagedAtMillis = cursor.value("last_engagement_time_utc_millis")
                                ?.toLongOrNull()?.takeIf { it > 0 },
                        ),
                    )
                }
            }.distinctBy { it.packageName to it.intentUri }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

private fun Cursor.value(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
