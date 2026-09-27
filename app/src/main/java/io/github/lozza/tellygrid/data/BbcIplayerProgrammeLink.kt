package io.github.lozza.tellygrid.data

/**
 * The TV's Freeview playback activity receives the BBC AIT URL from its
 * wrapper. Replaying this four-field request opened the specific programme.
 *
 * The URL and playback contract below were captured from a real BBC
 * iPlayer recommendation on the reference TV.  We deliberately accept only
 * BBC's provider-owned AIT endpoint and an episode deeplink; titles and
 * airtimes are never converted into an iPlayer URL.
 */
object BbcIplayerProgrammeLink {
    const val PROVIDER_PACKAGE_NAME = "uk.co.freeview.bbc"
    const val PACKAGE_NAME = "org.droidtv.epgdata"
    const val ACTIVITY = "org.droidtv.epgdata.freeviewplay.ui.FvpPlaybackActivity"
    const val ACTION = "org.droidtv.tv.intent.action.DUKTPCONTENTPLAYBACK"
    const val MIME_TYPE = "fvp/request"
    const val CALLER = "crid://bbc.co.uk/nitro/application/ondemand/iplayer"

    private val endpoint = Regex(
        "^https://www\\.live\\.bbctvapps\\.co\\.uk/tap/iplayer/ait/launch/iplayer\\.aitx\\?.+$",
        RegexOption.IGNORE_CASE,
    )

    private val episodeId = Regex("^[a-z0-9]{8,15}$")

    /**
     * Freeview Play AIT launch for a BBC-issued episode id, in the same format as
     * the captured *The Split Up* recommendation. The id must come from BBC's own
     * catalogue ([BbcIplayerCatalogueRepository]), never from a guide title.
     */
    fun aitUrlForEpisode(id: String): String? =
        id.takeIf(episodeId::matches)?.let {
            "https://www.live.bbctvapps.co.uk/tap/iplayer/ait/launch/iplayer.aitx" +
                "?deeplink=tv/playback/urn:bbc:iplayer:episode:$it" +
                "&campaign=catalogue&medium=referral&partner=net.freeviewplay"
        }

    /** Public episode page, for retail iPlayer builds that claim bbc.co.uk links. */
    fun webUrlForEpisode(id: String): String? =
        id.takeIf(episodeId::matches)?.let { "https://www.bbc.co.uk/iplayer/episode/$it" }

    fun verifiedOrNull(uri: String?): String? =
        uri?.trim()?.takeIf(::isVerified)

    private fun isVerified(uri: String): Boolean {
        if (!endpoint.matches(uri)) return false
        val encodedDeeplink = uri.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.substringBefore('=').equals("deeplink", ignoreCase = true) }
            ?.substringAfter('=', "")
            ?: return false
        val deeplink = runCatching {
            java.net.URLDecoder.decode(encodedDeeplink, Charsets.UTF_8.name())
        }.getOrDefault(encodedDeeplink)
        return Regex("^tv/playback/urn:bbc:iplayer:episode:[A-Za-z0-9_-]+$", RegexOption.IGNORE_CASE)
            .matches(deeplink)
    }

    fun intentOrNull(uri: String?): ProviderProgrammeIntent? =
        verifiedOrNull(uri)?.let {
            ProviderProgrammeIntent(
                packageName = PACKAGE_NAME,
                providerPackageName = PROVIDER_PACKAGE_NAME,
                action = ACTION,
                componentClassName = ACTIVITY,
                mimeType = MIME_TYPE,
                stringExtras = mapOf(
                    "Request" to "FVP_AIT_URL",
                    "Mode" to "CNT",
                    "Detail" to it,
                    "Caller" to CALLER,
                ),
            )
        }
}
