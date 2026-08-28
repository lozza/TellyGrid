package io.github.lozza.tellygrid.data

import java.time.Instant

enum class ProviderId {
    BBC_IPLAYER,
    ITVX,
    CHANNEL_4,
    FIVE,
    U,
    NOW,
    DISCOVERY_PLUS,
    OWNED_STREAM
}

data class ProviderApp(
    val id: ProviderId,
    val displayName: String,
    val packageCandidates: List<String>,
    val discoveryTerms: List<String>,
    val discoveryUris: List<String>,
)

sealed interface PlaybackTarget {
    /**
     * Use only for media the app owner is contractually allowed to distribute.
     * A manifest URL being technically reachable is not permission to restream it.
     */
    data class LicensedStream(
        val streamUri: String,
        val drmLicenseUri: String? = null,
    ) : PlaybackTarget

    /**
     * `channelUri` stays null until a provider channel link has been verified on a
     * supported TV device; the installed app remains the safe fallback.
     */
    data class ProviderHandoff(
        val provider: ProviderApp,
        val channelUri: String? = null,
        /** Freeview logical channel number used for local in-app TV-tuner playback. */
        val terrestrialLcn: Int? = null,
    ) : PlaybackTarget

    data class Unavailable(val reason: String) : PlaybackTarget
}

data class Programme(
    val id: String,
    val title: String,
    val synopsis: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class GuideChannel(
    val id: String,
    val number: Int,
    val name: String,
    val providerLabel: String,
    val accentArgb: Long,
    val playback: PlaybackTarget,
    val programmes: List<Programme>,
)
