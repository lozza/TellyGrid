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
    HBO_MAX,
    PLUTO_TV,
    OWNED_STREAM
}

/** A stable, user-facing grouping. It is never derived from an external XMLTV name. */
enum class GuideCategory {
    ENTERTAINMENT,
    MOVIES,
    SPORT,
    KIDS,
    NEWS,
    DOCUMENTARY,
    FAST,
}

data class ProviderApp(
    val id: ProviderId,
    val displayName: String,
    val packageCandidates: List<String>,
    val discoveryTerms: List<String>,
    val discoveryUris: List<String>,
)

/**
 * A provider-issued programme intent. This is deliberately separate from a
 * plain URI: some TV apps require a MIME type and named extras rather than
 * ACTION_VIEW.
 *
 * Instances must come from a verified provider/launcher handoff. Titles and
 * airtimes are not sufficient to construct one.
 */
data class ProviderProgrammeIntent(
    val packageName: String,
    val action: String,
    /** Installed provider selected by the user when its verified handoff uses a system intermediary. */
    val providerPackageName: String = packageName,
    val componentClassName: String? = null,
    val mimeType: String? = null,
    val dataUri: String? = null,
    val categories: Set<String> = emptySet(),
    val stringExtras: Map<String, String> = emptyMap(),
) {
    fun isComplete(): Boolean {
        if (packageName.isBlank() || providerPackageName.isBlank() || action.isBlank()) return false
        if (mimeType.isNullOrBlank() && dataUri.isNullOrBlank() && stringExtras.isEmpty()) return false
        if (action == "android.intent.action.VIEW" && dataUri.isNullOrBlank()) return false
        if (action == "android.intent.action.SEND" &&
            (mimeType.isNullOrBlank() || stringExtras.isEmpty())
        ) return false
        return true
    }

    fun matchesSelectedPackage(selectedPackage: String?): Boolean =
        selectedPackage == null || selectedPackage == providerPackageName
}

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
        /** Stable TellyGrid catalog id used by opt-in provider navigation helpers. */
        val channelId: String,
        val channelUri: String? = null,
        /** Exact provider-owned programme link, when the guide source supplies one. */
        val contentUri: String? = null,
        /** Verified provider intent for a specific programme, when available. */
        val programmeIntent: ProviderProgrammeIntent? = null,
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
    val imageUri: String? = null,
    val episodeLabel: String? = null,
    val contentUri: String? = null,
    /** Provider-issued intent metadata; never inferred from the programme title. */
    val programmeIntent: ProviderProgrammeIntent? = null,
)

data class GuideChannel(
    val id: String,
    val number: Int,
    val name: String,
    val providerLabel: String,
    val accentArgb: Long,
    val logoUri: String? = null,
    val category: GuideCategory = GuideCategory.ENTERTAINMENT,
    val playback: PlaybackTarget,
    val programmes: List<Programme>,
)
