package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.lozza.tellygrid.update.AppUpdateRelease
import io.github.lozza.tellygrid.update.AppUpdateState
import io.github.lozza.tellygrid.update.AppUpdater
import kotlinx.coroutines.launch

/** One-line summary for the Settings row that opens [AppUpdateScreen]. */
fun appUpdateRowDetail(): String = when (val state = AppUpdater.state) {
    is AppUpdateState.Checking -> "Checking GitHub…"
    is AppUpdateState.UpToDate -> "Up to date (${AppUpdater.installedVersion})"
    is AppUpdateState.Downloading -> "Downloading ${state.release.version}…"
    else -> AppUpdater.availableUpdate?.let { "Update available: ${it.version} — you have ${AppUpdater.installedVersion}" }
        ?: "Version ${AppUpdater.installedVersion} — check for a newer release"
}

@Composable
fun AppUpdateScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = AppUpdater.state
    val release = AppUpdater.availableUpdate

    // Opening the page always asks GitHub again, unless an update is already in hand.
    LaunchedEffect(Unit) {
        if (release == null) AppUpdater.check(context)
    }
    // Coming back from Android's installer or settings means the user cancelled or backed out.
    LifecycleResumeEffect(Unit) {
        AppUpdater.returnedToApp()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxSize().background(ScreenBackground).then(ScreenPadding)) {
        ScreenTitle("APP UPDATES", "TellyGrid ${AppUpdater.installedVersion} is installed. Updates come from TellyGrid's GitHub releases.")
        Spacer(Modifier.height(16.dp))

        Text(statusLine(state), color = ScreenPaper, fontSize = 16.sp)
        if (state is AppUpdateState.Failed) {
            Text(state.message, color = Color(0xFFFFB4B4), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (state is AppUpdateState.Installing) {
            Text(state.message, color = ScreenMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            if (release != null) {
                item {
                    SettingsRow(
                        title = "Install ${release.version}",
                        detail = if (state is AppUpdateState.Downloading) {
                            downloadProgress(state)
                        } else {
                            "Download ${sizeLabel(release)} and update TellyGrid. Your settings are kept."
                        },
                        action = if (state is AppUpdateState.Downloading) "WAIT" else "INSTALL",
                        onClick = {
                            if (state !is AppUpdateState.Downloading) {
                                scope.launch { AppUpdater.downloadAndInstall(context, release) }
                            }
                        },
                        requestInitialFocus = true,
                    )
                }
            }
            item {
                SettingsRow(
                    title = "Check for updates",
                    detail = "Look for a newer TellyGrid release now",
                    action = "CHECK",
                    onClick = { scope.launch { AppUpdater.check(context) } },
                    requestInitialFocus = release == null,
                )
            }
            if (release != null && release.notes.isNotBlank()) {
                item {
                    SettingsSection("What's new in ${release.version}")
                    Text(
                        plainNotes(release.notes),
                        color = ScreenMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 9,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun statusLine(state: AppUpdateState): String = when (state) {
    is AppUpdateState.Idle, is AppUpdateState.Checking -> "Checking for updates…"
    is AppUpdateState.UpToDate -> "You're on the latest version." +
        (state.latest?.let { " (Newest release: ${it.version})" } ?: "")
    is AppUpdateState.Available -> "TellyGrid ${state.release.version} is available."
    is AppUpdateState.Downloading -> "Downloading TellyGrid ${state.release.version}…"
    is AppUpdateState.Installing -> "Installing TellyGrid ${state.release.version}"
    is AppUpdateState.Failed -> if (state.release != null) "Couldn't install TellyGrid ${state.release.version}" else "Couldn't check for updates"
}

private fun downloadProgress(state: AppUpdateState.Downloading): String {
    val done = state.downloadedBytes / (1024 * 1024)
    return if (state.totalBytes > 0) {
        "Downloading… ${(state.downloadedBytes * 100 / state.totalBytes).coerceIn(0, 100)}% (${done} of ${state.totalBytes / (1024 * 1024)} MB)"
    } else {
        "Downloading… ${done} MB"
    }
}

private fun sizeLabel(release: AppUpdateRelease): String =
    if (release.apkSizeBytes > 0) "%.1f MB".format(release.apkSizeBytes / (1024.0 * 1024.0)) else "the update"

/** Release notes are Markdown; show them as plain text on the TV. */
private fun plainNotes(markdown: String): String = markdown
    .replace("\r\n", "\n")
    .lines()
    .joinToString("\n") { line ->
        line.replace(Regex("^#+\\s*"), "")
            .replace(Regex("^\\s*[-*]\\s+"), "•  ")
            .replace("**", "")
            .replace("`", "")
    }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
