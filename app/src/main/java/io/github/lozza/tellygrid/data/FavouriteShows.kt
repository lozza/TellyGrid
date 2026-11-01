package io.github.lozza.tellygrid.data

import java.time.Instant

/** Where a favourite came from, which decides how it is opened again later. */
enum class FavouriteSource { APP, BBC_IPLAYER, GUIDE }

/**
 * A show the viewer saved to Favourites, like Sky/Virgin's "record" button. Everything
 * needed to open it again is stored locally, so it still works if the row it was saved
 * from has changed.
 */
data class FavouriteShow(
    /** Stable identity: the same show saved twice from the same source is one favourite. */
    val key: String,
    val source: FavouriteSource,
    val title: String,
    val subtitle: String? = null,
    val artworkUri: String? = null,
    /** APP: the app that published the programme and its launch intent. */
    val packageName: String? = null,
    val intentUri: String? = null,
    /** BBC_IPLAYER: the BBC episode id it was saved from. */
    val bbcEpisodeId: String? = null,
    /** GUIDE: the channel it was saved from. Any later airing of the same title matches. */
    val channelId: String? = null,
    val channelName: String? = null,
    val addedAtMillis: Long = 0L,
)

/** Lower-case letters and digits only, so "Doctor Who" and "DOCTOR WHO " match. */
internal fun normaliseShowTitle(title: String): String =
    title.lowercase().filter { it.isLetterOrDigit() }

fun favouriteKeyForApp(packageName: String, title: String) = "app|$packageName|${normaliseShowTitle(title)}"
fun favouriteKeyForBbc(title: String) = "bbc|${normaliseShowTitle(title)}"
fun favouriteKeyForGuide(title: String) = "guide|${normaliseShowTitle(title)}"

fun TvPublishedProgramme.asFavourite(appLabel: String) = FavouriteShow(
    key = favouriteKeyForApp(packageName, title),
    source = FavouriteSource.APP,
    title = title,
    subtitle = appLabel,
    artworkUri = artworkUri,
    packageName = packageName,
    intentUri = intentUri,
)

fun BbcIplayerEpisode.asFavourite() = FavouriteShow(
    key = favouriteKeyForBbc(title),
    source = FavouriteSource.BBC_IPLAYER,
    title = title,
    subtitle = "BBC iPlayer",
    artworkUri = artworkUri,
    bbcEpisodeId = id,
)

fun Programme.asFavourite(channel: GuideChannel) = FavouriteShow(
    key = favouriteKeyForGuide(title),
    source = FavouriteSource.GUIDE,
    title = title,
    subtitle = channel.name,
    artworkUri = imageUri,
    channelId = channel.id,
    channelName = channel.name,
)

/** The programme a guide favourite should open, and whether it is on now. */
data class FavouriteAiring(val channel: GuideChannel, val programme: Programme, val onNow: Boolean)

/**
 * Finds the airing of a guide favourite to show or open: one on now first, otherwise the
 * next one to start. The channel it was saved from wins a tie, like a series link.
 */
fun FavouriteShow.nextAiring(channels: List<GuideChannel>, now: Instant): FavouriteAiring? {
    if (source != FavouriteSource.GUIDE) return null
    val wanted = normaliseShowTitle(title)
    val airings = channels.flatMap { channel ->
        channel.programmes
            .filter { it.endsAt.isAfter(now) && normaliseShowTitle(it.title) == wanted }
            .map { channel to it }
    }
    val preferSaved = compareBy<Pair<GuideChannel, Programme>> { if (it.first.id == channelId) 0 else 1 }
    val onNow = airings.filter { !it.second.startsAt.isAfter(now) }.sortedWith(preferSaved).firstOrNull()
    if (onNow != null) return FavouriteAiring(onNow.first, onNow.second, onNow = true)
    val next = airings.sortedWith(compareBy<Pair<GuideChannel, Programme>> { it.second.startsAt }.then(preferSaved))
        .firstOrNull() ?: return null
    return FavouriteAiring(next.first, next.second, onNow = false)
}

private val base64Encoder = java.util.Base64.getUrlEncoder().withoutPadding()
private val base64Decoder = java.util.Base64.getUrlDecoder()

/** Tab-separated, Base64 fields, so titles with any characters survive the round trip. */
internal fun encodeFavouriteShow(show: FavouriteShow): String = listOf(
    "1",
    show.key,
    show.source.name,
    show.title,
    show.subtitle.orEmpty(),
    show.artworkUri.orEmpty(),
    show.packageName.orEmpty(),
    show.intentUri.orEmpty(),
    show.bbcEpisodeId.orEmpty(),
    show.channelId.orEmpty(),
    show.channelName.orEmpty(),
    show.addedAtMillis.toString(),
).joinToString("\t") { base64Encoder.encodeToString(it.toByteArray(Charsets.UTF_8)) }

internal fun decodeFavouriteShow(value: String): FavouriteShow? {
    val fields = runCatching {
        value.split('\t').map { String(base64Decoder.decode(it), Charsets.UTF_8) }
    }.getOrNull() ?: return null
    if (fields.size < 12 || fields[0] != "1") return null
    val key = fields[1].takeIf(String::isNotBlank) ?: return null
    val source = runCatching { enumValueOf<FavouriteSource>(fields[2]) }.getOrNull() ?: return null
    val title = fields[3].takeIf(String::isNotBlank) ?: return null
    return FavouriteShow(
        key = key,
        source = source,
        title = title,
        subtitle = fields[4].ifBlank { null },
        artworkUri = fields[5].ifBlank { null },
        packageName = fields[6].ifBlank { null },
        intentUri = fields[7].ifBlank { null },
        bbcEpisodeId = fields[8].ifBlank { null },
        channelId = fields[9].ifBlank { null },
        channelName = fields[10].ifBlank { null },
        addedAtMillis = fields[11].toLongOrNull() ?: 0L,
    )
}
