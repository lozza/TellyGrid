package io.github.lozza.tellygrid.data

import java.time.Duration
import java.time.Instant
import java.util.Locale

/** A local-only search result. No viewing or search data leaves the television. */
data class GuideSearchResult(
    val channel: GuideChannel,
    val programme: Programme,
)

enum class GuideSearchSection(val label: String) {
    ON_NOW("On now"),
    STARTING_SOON("Starting soon"),
    LATER("Later"),
}

data class GuideSearchGroup(
    val section: GuideSearchSection,
    val results: List<GuideSearchResult>,
)

/**
 * Searches the guide already held on device. Results deliberately exclude ended
 * listings: opening a result should always take the viewer to something current
 * or upcoming in the grid, never to a stale programme tile.
 */
fun searchGuide(
    channels: List<GuideChannel>,
    query: String,
    now: Instant = Instant.now(),
    startingSoonWindow: Duration = Duration.ofHours(2),
): List<GuideSearchGroup> {
    val needle = query.normalisedSearchText()
    if (needle.isBlank()) return emptyList()

    val matches = channels.flatMap { channel ->
        channel.programmes.asSequence()
            .filter { it.endsAt > now }
            .filter { programme ->
                listOf(
                    programme.title,
                    programme.synopsis,
                    programme.episodeLabel.orEmpty(),
                    channel.name,
                    channel.providerLabel,
                ).any { it.normalisedSearchText().contains(needle) }
            }
            .map { programme -> GuideSearchResult(channel, programme) }
            .toList()
    }

    return GuideSearchSection.values().mapNotNull { section ->
        val results = matches.filter { result ->
            when (section) {
                GuideSearchSection.ON_NOW ->
                    result.programme.startsAt <= now && result.programme.endsAt > now
                GuideSearchSection.STARTING_SOON ->
                    result.programme.startsAt > now && result.programme.startsAt <= now.plus(startingSoonWindow)
                GuideSearchSection.LATER -> result.programme.startsAt > now.plus(startingSoonWindow)
            }
        }.sortedWith(
            compareBy<GuideSearchResult> { it.programme.startsAt }
                .thenBy { it.channel.number }
                .thenBy { it.programme.title },
        )
        results.takeIf(List<GuideSearchResult>::isNotEmpty)?.let { GuideSearchGroup(section, it) }
    }
}

/** The grid always begins on a half-hour boundary. */
fun searchResultTimelineStart(programme: Programme): Long =
    (programme.startsAt.toEpochMilli() / HALF_HOUR_MILLIS) * HALF_HOUR_MILLIS

private fun String.normalisedSearchText(): String = trim().lowercase(Locale.ROOT)

private const val HALF_HOUR_MILLIS = 30L * 60L * 1_000L
