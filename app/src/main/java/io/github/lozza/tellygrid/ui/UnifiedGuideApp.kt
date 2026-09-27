package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.GuideFilter
import io.github.lozza.tellygrid.data.GuideDensity
import io.github.lozza.tellygrid.data.GuideSettings
import io.github.lozza.tellygrid.data.GuideReminder
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.ProviderApp
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.data.Programme
import io.github.lozza.tellygrid.data.forGuideSettings
import io.github.lozza.tellygrid.data.sameProgramme
import io.github.lozza.tellygrid.data.searchResultTimelineStart
import io.github.lozza.tellygrid.playback.InstalledTvApp
import io.github.lozza.tellygrid.playback.LaunchResult
import io.github.lozza.tellygrid.playback.NativeTvChannel

private fun InstalledTvApp.isFreeviewWrapper(): Boolean {
    val searchable = "$label $packageName".lowercase()
    return searchable.contains("freeview") || packageName == "uk.co.freeview.uktv"
}

@Composable
fun UnifiedGuideApp(
    channels: List<GuideChannel>,
    guideStatus: String,
    onHandoff: (PlaybackTarget.ProviderHandoff) -> LaunchResult,
    onLaunchInstalledApp: (InstalledTvApp) -> LaunchResult,
    onLaunchPublishedProgramme: (io.github.lozza.tellygrid.data.TvPublishedProgramme) -> LaunchResult,
    publishedRecommendations: io.github.lozza.tellygrid.data.TvPublishedRecommendations,
    tvListingsAllowed: Boolean,
    onRequestTvListingsPermission: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onEnableHomeOverride: () -> Unit,
    onOpenAndroidSettings: () -> Boolean,
    launcherAppOrder: List<String>,
    onLauncherAppOrderChanged: (List<String>) -> Unit,
    onResolveNativeChannel: (Int) -> NativeTvChannel?,
    onNativeChannelFallback: (NativeTvChannel) -> LaunchResult,
    providers: List<ProviderApp>,
    installedApps: List<InstalledTvApp>,
    suggestedApps: Map<ProviderId, List<InstalledTvApp>>,
    selections: Map<ProviderId, String>,
    guideSettings: GuideSettings,
    discoveryAccessibilitySupported: Boolean,
    discoveryAccessibilityEnabled: Boolean,
    onProviderSelected: (ProviderId, String?) -> Unit,
    onProviderEnabledChanged: (ProviderId) -> Unit,
    onGuideFilterChanged: (GuideFilter) -> Unit,
    onGuideProviderFilterChanged: (ProviderId?) -> Unit,
    onClearGuideFilters: () -> Unit,
    onGuideSearchResult: (String, String, Long) -> Unit,
    onGuideTimelineChanged: (Long) -> Unit,
    onGuideSelectionChanged: (String?, String?) -> Unit,
    onFavouriteChanged: (String) -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onReminderChanged: (GuideReminder) -> Unit,
    onChannelVisibilityChanged: (String) -> Unit,
    onChannelOrderChanged: (List<String>) -> Unit,
    onResetChannelOrder: () -> Unit,
    onGuideDensityChanged: (GuideDensity) -> Unit,
    onResetChannelManagement: () -> Unit,
) {
    var playerTarget by remember { mutableStateOf<PlaybackTarget.LicensedStream?>(null) }
    var nativeChannel by remember { mutableStateOf<NativeTvChannel?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var showingSetup by remember { mutableStateOf(false) }
    var setupOpenedFromHome by remember { mutableStateOf(false) }
    var showingFilter by remember { mutableStateOf(false) }
    var showingSearch by remember { mutableStateOf(false) }
    var showingHome by rememberSaveable { mutableStateOf(true) }
    var showingApps by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<Pair<GuideChannel, io.github.lozza.tellygrid.data.Programme>?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val featured = remember(channels) {
        val now = java.time.Instant.now()
        channels.asSequence()
            .flatMap { channel -> channel.programmes.asSequence().map { programme -> channel to programme } }
            .firstOrNull { (_, programme) -> programme.startsAt <= now && now < programme.endsAt }
            ?: channels.asSequence()
                .flatMap { channel -> channel.programmes.asSequence().map { programme -> channel to programme } }
                .firstOrNull { (_, programme) -> programme.endsAt > now }
    }
    val linkedProviderApps = remember(installedApps, suggestedApps, selections, providers) {
        providers.mapNotNull { provider ->
            val selected = selections[provider.id]
            val selectedApp = selected?.let { packageName ->
                installedApps.firstOrNull { it.packageName == packageName }
            }
            // A stale selection can point NOW at the Freeview Play wrapper.
            // Keep provider identity and artwork aligned with the actual app.
            val app = selectedApp
                ?.takeUnless { provider.id == ProviderId.NOW && it.isFreeviewWrapper() }
                ?: suggestedApps[provider.id].orEmpty()
                    .firstOrNull { provider.id != ProviderId.NOW || !it.isFreeviewWrapper() }
            app?.let { provider.id to it }
        }.toMap()
    }

    fun launchProviderTarget(target: PlaybackTarget.ProviderHandoff): LaunchResult {
        val handoff = onHandoff(target)
        if (handoff is LaunchResult.Failed) {
            // A provider can expose a guide channel without exposing a deep link
            // on this particular TV build. Keep every launcher shelf actionable by
            // falling back to the mapped installed TV app instead of silently doing
            // nothing (notably U and discovery+).
            linkedProviderApps[target.provider.id]?.let { app ->
                val appResult = onLaunchInstalledApp(app)
                if (appResult is LaunchResult.Opened) return appResult
            }
        }
        return handoff
    }

    fun launch(channel: GuideChannel, programme: io.github.lozza.tellygrid.data.Programme) {
        when (val target = channel.playback) {
            is PlaybackTarget.LicensedStream -> playerTarget = target
            is PlaybackTarget.ProviderHandoff -> {
                val programmeTarget = target.copy(
                    contentUri = programme.contentUri,
                    programmeIntent = programme.programmeIntent,
                )
                val freeviewWrapperPackage = when (target.provider.id) {
                    ProviderId.BBC_IPLAYER -> "uk.co.freeview.bbc"
                    ProviderId.ITVX -> "uk.co.freeview.itv"
                    else -> null
                }
                val selectedPackage = selections[target.provider.id]
                    ?.takeUnless { target.provider.id == ProviderId.NOW &&
                        installedApps.firstOrNull { app -> app.packageName == it }?.isFreeviewWrapper() == true }
                val usesFreeviewWrapper = freeviewWrapperPackage != null &&
                    if (selectedPackage != null) {
                        selectedPackage == freeviewWrapperPackage
                    } else {
                        suggestedApps[target.provider.id].orEmpty()
                            .any { it.packageName == freeviewWrapperPackage }
                    }
                val prefersRetailBroadcasterApp = freeviewWrapperPackage != null && !usesFreeviewWrapper

                if (prefersRetailBroadcasterApp && programme.contentUri == null) {
                    status = when (val result = launchProviderTarget(programmeTarget)) {
                        is LaunchResult.Opened -> "Opening ${result.destination}"
                        is LaunchResult.Failed -> result.message
                    }
                    return
                }
                val resolved = if (programme.contentUri == null) {
                    target.terrestrialLcn?.let(onResolveNativeChannel)
                } else {
                    null
                }
                if (resolved != null) {
                    nativeChannel = resolved
                    return
                }
                status = when (val result = launchProviderTarget(programmeTarget)) {
                    is LaunchResult.Opened -> "Opening ${result.destination}"
                    is LaunchResult.Failed -> result.message
                }
            }
            is PlaybackTarget.Unavailable -> status = target.reason
        }
    }

    fun launchFromHome(channel: GuideChannel, programme: io.github.lozza.tellygrid.data.Programme) {
        when (val target = channel.playback) {
            is PlaybackTarget.ProviderHandoff -> {
                status = when (val result = launchProviderTarget(target.copy(
                    contentUri = programme.contentUri,
                    programmeIntent = programme.programmeIntent,
                ))) {
                    is LaunchResult.Opened -> "Opening ${result.destination}"
                    is LaunchResult.Failed -> result.message
                }
            }
            else -> launch(channel, programme)
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF70C8FF),
            background = Color(0xFF090D16),
            surface = Color(0xFF151B28),
            onBackground = Color.White,
            onSurface = Color.White,
        ),
    ) {
        val playing = playerTarget
        if (showingHome) {
            if (showingApps) {
                LauncherAppsScreen(
                    installedApps = installedApps,
                    appOrder = launcherAppOrder,
                    onAppOrderChanged = onLauncherAppOrderChanged,
                    onLaunchApp = { app ->
                        status = when (val result = onLaunchInstalledApp(app)) {
                            is LaunchResult.Opened -> "Opening ${result.destination}"
                            is LaunchResult.Failed -> result.message
                        }
                    },
                    onBack = { showingApps = false },
                )
            } else {
                LauncherHomeScreen(
                    guideStatus = status ?: guideStatus,
                    featuredChannel = featured?.first,
                    featuredProgramme = featured?.second,
                    guideChannels = channels,
                    linkedProviderApps = linkedProviderApps,
                    installedApps = installedApps,
                    publishedRecommendations = publishedRecommendations,
                    tvListingsAllowed = tvListingsAllowed,
                    onRequestTvListingsPermission = onRequestTvListingsPermission,
                    onOpenGuide = { showingHome = false },
                    onWatchProgramme = { channel, programme -> launchFromHome(channel, programme) },
                    onLaunchApp = { app ->
                        status = when (val result = onLaunchInstalledApp(app)) {
                            is LaunchResult.Opened -> "Opening ${result.destination}"
                            is LaunchResult.Failed -> result.message
                        }
                    },
                    onOpenPublishedProgramme = { programme ->
                        status = when (val result = onLaunchPublishedProgramme(programme)) {
                            is LaunchResult.Opened -> "Opening ${result.destination}"
                            is LaunchResult.Failed -> result.message
                        }
                    },
                    onOpenApps = { showingApps = true },
                    onOpenSettings = {
                        setupOpenedFromHome = true
                        showingHome = false
                        showingSetup = true
                    },
                    onSetDefaultLauncher = onOpenHomeSettings,
                    onEnableHomeOverride = onEnableHomeOverride,
                    launcherAppOrder = launcherAppOrder,
                )
            }
        } else if (details != null) {
            val (channel, programme) = details!!
            ProgrammeDetailsScreen(
                channel = channel,
                programme = programme,
                isFavourite = channel.id in guideSettings.favouriteChannelIds,
                hasReminder = guideSettings.reminders.any {
                    it.sameProgramme(GuideReminder.forProgramme(channel, programme))
                },
                canRemind = programme.startsAt.isAfter(java.time.Instant.now()),
                notificationsAllowed = notificationsAllowed,
                onWatch = {
                    details = null
                    launch(channel, programme)
                },
                onToggleFavourite = { onFavouriteChanged(channel.id) },
                onRequestNotificationPermission = onRequestNotificationPermission,
                onToggleReminder = { onReminderChanged(GuideReminder.forProgramme(channel, programme)) },
                onHideChannel = {
                    details = null
                    onChannelVisibilityChanged(channel.id)
                },
                onBack = { details = null },
            )
        } else if (showingFilter) {
            GuideFilterScreen(
                settings = guideSettings,
                providers = providers,
                onFilterSelected = onGuideFilterChanged,
                onProviderSelected = onGuideProviderFilterChanged,
                onClearFilters = onClearGuideFilters,
                onBack = { showingFilter = false },
            )
        } else if (showingSearch) {
            GuideSearchScreen(
                channels = channels.forGuideSettings(guideSettings.copy(filter = GuideFilter.ALL, providerFilter = null)),
                onResultSelected = { channel, programme ->
                    onGuideSearchResult(channel.id, programme.id, searchResultTimelineStart(programme))
                    showingSearch = false
                },
                onBack = { showingSearch = false },
            )
        } else if (showingSetup) {
            ProviderSetupScreen(
                providers = providers,
                installedApps = installedApps,
                suggestedApps = suggestedApps,
                selections = selections,
                channels = channels,
                guideSettings = guideSettings,
                discoveryAccessibilitySupported = discoveryAccessibilitySupported,
                discoveryAccessibilityEnabled = discoveryAccessibilityEnabled,
                onSelect = onProviderSelected,
                onProviderEnabledChanged = onProviderEnabledChanged,
                onGuideDensityChanged = onGuideDensityChanged,
                onToggleHidden = onChannelVisibilityChanged,
                onOrderChanged = onChannelOrderChanged,
                onResetOrder = onResetChannelOrder,
                onResetChannelManagement = onResetChannelManagement,
                onOpenAndroidSettings = onOpenAndroidSettings,
                onBack = {
                    showingSetup = false
                    if (setupOpenedFromHome) showingHome = true
                    setupOpenedFromHome = false
                },
            )
        } else if (nativeChannel != null) {
            val channel = nativeChannel!!
            BackHandler { nativeChannel = null }
            NativeTvPlayerScreen(
                channel = channel,
                onBack = { nativeChannel = null },
                onTuneFailed = {
                    nativeChannel = null
                    status = when (val result = onNativeChannelFallback(channel)) {
                        is LaunchResult.Opened -> "Opening ${result.destination}"
                        is LaunchResult.Failed -> result.message
                    }
                },
            )
        } else if (playing != null) {
            BackHandler { playerTarget = null }
            PlayerScreen(target = playing, onBack = { playerTarget = null })
        } else {
            val visibleChannels = remember(channels, guideSettings) { channels.forGuideSettings(guideSettings) }
            saveableStateHolder.SaveableStateProvider("guide") {
                GuideScreen(
                    channels = visibleChannels,
                    status = status ?: guideStatus,
                    settings = guideSettings,
                    onOpenHome = {
                        details = null
                        showingFilter = false
                        showingSearch = false
                        showingSetup = false
                        showingHome = true
                    },
                    onOpenSetup = {
                        setupOpenedFromHome = false
                        showingSetup = true
                    },
                    onOpenSearch = { showingSearch = true },
                    onOpenFilter = { showingFilter = true },
                    onClearFilters = onClearGuideFilters,
                    onTimelineChanged = onGuideTimelineChanged,
                    onSelectionChanged = onGuideSelectionChanged,
                    onProgrammeSelected = { channel, programme ->
                        launch(channel, programme)
                    },
                    onProgrammeDetails = { channel, programme -> details = channel to programme },
                )
            }
        }
    }
}
