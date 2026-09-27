package io.github.lozza.tellygrid.data

/**
 * Prime Video's Android TV activity accepts provider-owned HTTPS detail links.
 * This validator intentionally accepts only canonical Prime/Amazon detail URLs
 * with a provider-issued title id; titles must never be used to construct one.
 */
object PrimeVideoProgrammeLink {
    const val PACKAGE_NAME = "com.amazon.amazonvideo.livingroom"
    const val ACTIVITY = "com.amazon.ignition.IgnitionActivity"

    private val supported = Regex(
        "^https://(?:www\\.primevideo\\.com|watch\\.amazon\\.co\\.uk)/(?:-/[a-z]{2}/)?detail/[A-Za-z0-9]{16,}(?:[/?#].*)?$",
        RegexOption.IGNORE_CASE,
    )

    fun verifiedOrNull(uri: String?): String? = uri?.trim()?.takeIf(supported::matches)

    fun intentOrNull(uri: String?): ProviderProgrammeIntent? =
        verifiedOrNull(uri)?.let {
            ProviderProgrammeIntent(
                packageName = PACKAGE_NAME,
                action = "android.intent.action.VIEW",
                componentClassName = ACTIVITY,
                dataUri = it,
                categories = setOf("android.intent.category.BROWSABLE"),
            )
        }
}
