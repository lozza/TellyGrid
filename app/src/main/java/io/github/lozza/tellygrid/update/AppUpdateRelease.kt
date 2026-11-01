package io.github.lozza.tellygrid.update

import org.json.JSONArray

/** A GitHub release of TellyGrid that ships an installable APK. */
data class AppUpdateRelease(
    val version: String,
    val tagName: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSizeBytes: Long,
    val pageUrl: String,
    val prerelease: Boolean,
)

/**
 * Picks the newest release in a GitHub `/releases` response that has an `.apk` asset.
 *
 * Releases have been published as both pre-releases and full releases, so this deliberately
 * looks at every published (non-draft) release rather than GitHub's `/releases/latest`, which
 * ignores pre-releases.
 */
fun parseNewestApkRelease(json: String): AppUpdateRelease? {
    val releases = JSONArray(json)
    val candidates = mutableListOf<AppUpdateRelease>()
    for (index in 0 until releases.length()) {
        val release = releases.optJSONObject(index) ?: continue
        if (release.optBoolean("draft")) continue
        val tag = release.optString("tag_name").trim()
        val version = tag.removePrefix("v").removePrefix("V")
        if (parseVersion(version) == null) continue
        val assets = release.optJSONArray("assets") ?: continue
        val apk = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { asset ->
                asset.optString("name").endsWith(".apk", ignoreCase = true) &&
                    asset.optString("browser_download_url").startsWith("https://")
            } ?: continue
        candidates += AppUpdateRelease(
            version = version,
            tagName = tag,
            title = release.optString("name").ifBlank { tag },
            notes = release.optString("body"),
            apkUrl = apk.optString("browser_download_url"),
            apkName = apk.optString("name"),
            apkSizeBytes = apk.optLong("size"),
            pageUrl = release.optString("html_url"),
            prerelease = release.optBoolean("prerelease"),
        )
    }
    return candidates.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
}

/** True when [candidate] is a later version than [installed]. Unparseable versions never count. */
fun isNewerVersion(candidate: String, installed: String): Boolean {
    if (parseVersion(candidate) == null || parseVersion(installed) == null) return false
    return compareVersions(candidate, installed) > 0
}

/**
 * Semantic-version comparison: 0.8.0 > 0.8.0-alpha.9 > 0.8.0-alpha.8 > 0.7.0.
 * Unparseable versions sort below everything.
 */
fun compareVersions(a: String, b: String): Int {
    val left = parseVersion(a)
    val right = parseVersion(b)
    if (left == null || right == null) return compareValues(left != null, right != null)
    for (i in 0 until maxOf(left.core.size, right.core.size)) {
        val diff = left.core.getOrElse(i) { 0 }.compareTo(right.core.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    // A release without a pre-release tag is newer than any pre-release of the same core.
    if (left.pre.isEmpty() != right.pre.isEmpty()) return if (left.pre.isEmpty()) 1 else -1
    for (i in 0 until minOf(left.pre.size, right.pre.size)) {
        val l = left.pre[i]
        val r = right.pre[i]
        val ln = l.toLongOrNull()
        val rn = r.toLongOrNull()
        val diff = when {
            ln != null && rn != null -> ln.compareTo(rn)
            ln != null -> -1
            rn != null -> 1
            else -> l.compareTo(r, ignoreCase = true)
        }
        if (diff != 0) return diff
    }
    return left.pre.size.compareTo(right.pre.size)
}

private class ParsedVersion(val core: List<Int>, val pre: List<String>)

private fun parseVersion(raw: String): ParsedVersion? {
    val version = raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')
    val coreText = version.substringBefore('-')
    val preText = version.substringAfter('-', missingDelimiterValue = "")
    val core = coreText.split('.').map { it.toIntOrNull() ?: return null }
    if (core.isEmpty() || core.size > 4) return null
    val pre = if (preText.isEmpty()) emptyList() else preText.split('.').filter { it.isNotEmpty() }
    return ParsedVersion(core, pre)
}
