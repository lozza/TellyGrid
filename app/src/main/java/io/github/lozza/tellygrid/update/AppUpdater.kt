package io.github.lozza.tellygrid.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import io.github.lozza.tellygrid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val latest: AppUpdateRelease?) : AppUpdateState
    data class Available(val release: AppUpdateRelease) : AppUpdateState
    data class Downloading(val release: AppUpdateRelease, val downloadedBytes: Long, val totalBytes: Long) : AppUpdateState
    /** The installer was opened, or the user was sent to allow installs first. */
    data class Installing(val release: AppUpdateRelease, val message: String) : AppUpdateState
    data class Failed(val message: String, val release: AppUpdateRelease? = null) : AppUpdateState
}

/**
 * In-app updates from GitHub Releases on lozza/TellyGrid.
 *
 * Android only installs an update over the existing app when both APKs are signed with the
 * same key, so release APKs must keep being built with the same keystore as earlier releases.
 */
object AppUpdater {
    const val RELEASES_API = "https://api.github.com/repos/lozza/TellyGrid/releases?per_page=20"
    const val RELEASES_PAGE = "https://github.com/lozza/TellyGrid/releases"
    private const val PREFS = "app_updates"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_NOTIFIED_VERSION = "notified_version"
    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val APK_MIME = "application/vnd.android.package-archive"

    val installedVersion: String get() = BuildConfig.VERSION_NAME

    var state by mutableStateOf<AppUpdateState>(AppUpdateState.Idle)
        private set

    /** Newest release found that is later than this build, if any. */
    val availableUpdate: AppUpdateRelease?
        get() = when (val current = state) {
            is AppUpdateState.Available -> current.release
            is AppUpdateState.Downloading -> current.release
            is AppUpdateState.Installing -> current.release
            is AppUpdateState.Failed -> current.release
            else -> null
        }

    /** Quiet launch-time check, at most every few hours, with a one-off toast per new version. */
    suspend fun checkIfDue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS) return
        check(context)
        val release = availableUpdate ?: return
        if (prefs.getString(KEY_NOTIFIED_VERSION, null) == release.version) return
        prefs.edit().putString(KEY_NOTIFIED_VERSION, release.version).apply()
        Toast.makeText(
            context,
            "TellyGrid ${release.version} is available. Go to Settings > App updates to install it.",
            Toast.LENGTH_LONG,
        ).show()
    }

    suspend fun check(context: Context) {
        if (state is AppUpdateState.Checking || state is AppUpdateState.Downloading) return
        state = AppUpdateState.Checking
        state = runCatching {
            val latest = withContext(Dispatchers.IO) { parseNewestApkRelease(httpGet(RELEASES_API)) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            if (latest != null && isNewerVersion(latest.version, installedVersion)) {
                AppUpdateState.Available(latest)
            } else {
                AppUpdateState.UpToDate(latest)
            }
        }.getOrElse { error ->
            AppUpdateState.Failed("Couldn't reach GitHub to check for updates (${error.message ?: error.javaClass.simpleName}).")
        }
    }

    suspend fun downloadAndInstall(context: Context, release: AppUpdateRelease) {
        if (state is AppUpdateState.Downloading) return
        state = AppUpdateState.Downloading(release, 0L, release.apkSizeBytes)
        val apk = runCatching {
            withContext(Dispatchers.IO) { download(context, release) }
        }.getOrElse { error ->
            state = AppUpdateState.Failed("Download failed (${error.message ?: error.javaClass.simpleName}). Try again.", release)
            return
        }

        verify(context, apk)?.let { problem ->
            apk.delete()
            state = AppUpdateState.Failed(problem, release)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val allowIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(allowIntent) }.isSuccess) {
                state = AppUpdateState.Installing(
                    release,
                    "Allow TellyGrid to install apps, then press Back and choose Install again.",
                )
                return
            }
            // Some TV settings apps have no per-app screen; the installer will ask instead.
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val opened = listOf(Intent.ACTION_VIEW, @Suppress("DEPRECATION") Intent.ACTION_INSTALL_PACKAGE).any { action ->
            val intent = Intent(action)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }.isSuccess
        }
        state = if (opened) {
            AppUpdateState.Installing(release, "Confirm the update on screen. TellyGrid will close and reopen with ${release.version}.")
        } else {
            AppUpdateState.Failed("This TV has no app installer that TellyGrid can open. Install the APK from $RELEASES_PAGE instead.", release)
        }
    }

    private fun download(context: Context, release: AppUpdateRelease): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "TellyGrid-${release.version.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
        // Reuse a finished download, e.g. after a detour to allow installs from TellyGrid.
        if (target.isFile && release.apkSizeBytes > 0 && target.length() == release.apkSizeBytes) return target
        dir.listFiles()?.forEach { it.delete() }
        val partial = File(dir, target.name + ".part")
        val connection = open(release.apkUrl, accept = "application/octet-stream")
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
            var done = 0L
            var lastReported = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done - lastReported > 256 * 1024) {
                            lastReported = done
                            state = AppUpdateState.Downloading(release, done, total)
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("download incomplete")
        } finally {
            connection.disconnect()
        }
        if (!partial.renameTo(target)) throw IOException("couldn't save the download")
        return target
    }

    /** Returns a reason the APK can't update this install, or null when it looks fine. */
    private fun verify(context: Context, apk: File): String? {
        val pm = context.packageManager
        val archive = pm.archiveInfo(apk.path)
            ?: return "The downloaded file isn't a valid Android app. Try again later."
        if (archive.packageName != context.packageName) {
            return "The downloaded file isn't TellyGrid (${archive.packageName})."
        }
        val installed = pm.installedInfo(context.packageName)
        if (archive.longCode() < installed.longCode()) {
            return "The release on GitHub has an older build number than this one, so Android would refuse it."
        }
        val archiveSigners = signerDigests(archive)
        val installedSigners = signerDigests(installed)
        if (archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() && archiveSigners.intersect(installedSigners).isEmpty()) {
            return "This update is signed with a different key from the TellyGrid on this TV, so Android won't " +
                "install it over the top. Uninstall TellyGrid and install the new APK from GitHub instead " +
                "(your TellyGrid settings will be reset)."
        }
        return null
    }

    private fun httpGet(url: String): String {
        val connection = open(url, accept = "application/vnd.github+json")
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "TellyGrid/${BuildConfig.VERSION_NAME} (Android TV)")
        }

    @Suppress("DEPRECATION")
    private fun PackageManager.archiveInfo(path: String): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return runCatching { getPackageArchiveInfo(path, flags) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.installedInfo(packageName: String): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return getPackageInfo(packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { signing ->
                if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            }
        } else {
            info.signatures
        }
        val sha256 = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().filterNotNull().map { signature ->
            sha256.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
