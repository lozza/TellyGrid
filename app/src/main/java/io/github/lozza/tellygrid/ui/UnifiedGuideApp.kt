package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.ProviderApp
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.playback.InstalledTvApp
import io.github.lozza.tellygrid.playback.LaunchResult

@Composable
fun UnifiedGuideApp(
    channels: List<GuideChannel>,
    guideStatus: String,
    onHandoff: (PlaybackTarget.ProviderHandoff) -> LaunchResult,
    providers: List<ProviderApp>,
    installedApps: List<InstalledTvApp>,
    suggestedApps: Map<ProviderId, List<InstalledTvApp>>,
    selections: Map<ProviderId, String>,
    onProviderSelected: (ProviderId, String?) -> Unit,
) {
    var playerTarget by remember { mutableStateOf<PlaybackTarget.LicensedStream?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var showingSetup by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF35E0A1),
            background = Color(0xFF090D16),
            surface = Color(0xFF151B28),
            onBackground = Color.White,
            onSurface = Color.White,
        ),
    ) {
        val playing = playerTarget
        if (showingSetup) {
            ProviderSetupScreen(
                providers = providers,
                installedApps = installedApps,
                suggestedApps = suggestedApps,
                selections = selections,
                onSelect = onProviderSelected,
                onBack = { showingSetup = false },
            )
        } else if (playing != null) {
            BackHandler { playerTarget = null }
            PlayerScreen(target = playing, onBack = { playerTarget = null })
        } else {
            GuideScreen(
                channels = channels,
                status = status ?: guideStatus,
                onOpenSetup = { showingSetup = true },
                onChannelSelected = { channel ->
                    when (val target = channel.playback) {
                        is PlaybackTarget.LicensedStream -> playerTarget = target
                        is PlaybackTarget.ProviderHandoff -> {
                            status = when (val result = onHandoff(target)) {
                                is LaunchResult.Opened -> "Opening ${result.destination}"
                                is LaunchResult.Failed -> result.message
                            }
                        }
                        is PlaybackTarget.Unavailable -> status = target.reason
                    }
                },
            )
        }
    }
}
