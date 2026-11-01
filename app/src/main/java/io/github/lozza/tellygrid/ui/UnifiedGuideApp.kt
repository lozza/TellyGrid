package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.lozza.tellygrid.data.BbcIplayerEpisode
import io.github.lozza.tellygrid.data.FavouriteShow
import io.github.lozza.tellygrid.data.FavouriteSource
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.TvPublishedProgramme
import io.github.lozza.tellygrid.data.asFavourite
import io.github.lozza.tellygrid.data.favouriteKeyForBbc
import io.github.lozza.tellygrid.data.nextAiring
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
import io.github.lozza.tellygrid.playback.ButtonDestination
import io.github.lozza.tellygrid.playback.InstalledTvApp
import io.github.lozza.tellygrid.playback.LaunchResult
import io.github.lozza.tellygrid.playback.NativeTvChannel

/** One press of a mapped remote button; [sequence] makes repeat presses of the same button count. */
data class RemoteNavigation(val sequence: Int, val destination: ButtonDestination)

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
    bbcIplayerEpisodes: List<io.github.lozza.tellygrid.data.BbcIplayerEpisode>,
    onLaunchBbcIplayerEpisode: (io.github.lozza.tellygrid.data.BbcIplayerEpisode) -> LaunchResult,
    tvListingsAllowed: Boolean,
    onRequestTvListingsPermission: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onEnableHomeOverride: () -> Unit,
    onOpenAndroidSettings: () -> Boolean,
    launcherAppOrder: List<String>,
    onLauncherAppOrderChanged: (List<String>) -> Unit,
    hiddenWatchNextPackages: Set<String>,
    onToggleWatchNextApp: (String) -> Unit,
    onResolveNativeChannel: (Int) -> NativeTvChannel?,
    onNativeChannelFallback: (NativeTvChannel) -> LaunchResult,
    providers: List<ProviderApp>,
    installedApps: List<InstalledTvApp>,
    suggestedApps: Map<ProviderId, List<InstalledTvApp>>,
    selections: Map<ProviderId, String>,
    guideSettings: GuideSettings,
    onProviderSelected: (ProviderId, String?) -> Unit,
    onProviderEnabledChanged: (ProviderId) -> Unit,
    onGuideFilterChanged: (GuideFilter) -> Unit,
    onGuideProviderFilterChanged: (ProviderId?) -> Unit,
    onClearGuideFilters: () -> Unit,
    onGuideSearchResult: (String, String, Long) -> Unit,
    onGuideTimelineChanged: (Long) -> Unit,
    onGuideSelectionChanged: (String?, String?) -> Unit,
    onFavouriteChanged: (String) -> Unit,
    onFavouriteShowChanged: (FavouriteShow) -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onReminderChanged: (GuideReminder) -> Unit,
    onChannelVisibilityChanged: (String) -> Unit,
    onChannelOrderChanged: (List<String>) -> Unit,
    onResetChannelOrder: () -> Unit,
    onGuideDensityChanged: (GuideDensity) -> Unit,
    onResetChannelManagement: () -> Unit,
    homePresses: Int = 0,
    remoteNavigation: RemoteNavigation? = null,
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
    var showingFavourites by rememberSaveable { mutableStateOf(false) }
    // Details opened from Favourites (a show that isn't on yet) go back there, not to the guide.
    var detailsFromFavourites by remember { mutableStateOf(false) }
    val favouriteKeys = remember(guideSettings.favouriteShows) { guideSettings.favouriteShows.map { it.key }.toSet() }
    var details by remember { mutableStateOf<Pair<GuideChannel, io.github.lozza.tellygrid.data.Programme>?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()
    // The remote's Home button while TellyGrid is already open returns to the Home tab,
    // closing whatever screen is on top, like a normal TV launcher.
    // Bumped to start Settings afresh (its own sub-pages closed) when a remote button opens it.
    var settingsSession by remember { mutableStateOf(0) }
    fun closeEverything() {
        playerTarget = null
        nativeChannel = null
        details = null
        detailsFromFavourites = false
        showingSetup = false
        setupOpenedFromHome = false
        showingFilter = false
        showingSearch = false
        showingApps = false
        showingFavourites = false
        showingHome = true
        status = null
    }
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        closeEverything()
    }
    // A remote button mapped in Settings > Remote buttons jumps straight to its page.
    LaunchedEffect(remoteNavigation) {
        val destination = remoteNavigation?.destination ?: return@LaunchedEffect
        closeEverything()
        when (destination) {
            ButtonDestination.HOME -> Unit
            ButtonDestination.APPS -> showingApps = true
            ButtonDestination.FAVOURITES -> showingFavourites = true
            ButtonDestination.GUIDE -> showingHome = false
            ButtonDestination.SETTINGS -> {
                settingsSession++
                setupOpenedFromHome = true
                showingHome = false
                showingSetup = true
            }
        }
    }
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

    fun LaunchResult.describe(): String = when (this) {
        is LaunchResult.Opened -> "Opening $destination"
        is LaunchResult.Failed -> message
    }

    /** Opens a saved favourite the same way as the card it was saved from. */
    fun openFavourite(show: FavouriteShow) {
        when (show.source) {
            FavouriteSource.APP -> {
                // Prefer the app's current row for this show, as its link may have been refreshed.
                val current = (publishedRecommendations.watchNext + publishedRecommendations.preview)
                    .firstOrNull { it.asFavourite("").key == show.key }
                val programme = current ?: TvPublishedProgramme(
                    title = show.title,
                    packageName = show.packageName ?: return,
                    intentUri = show.intentUri ?: return,
                    artworkUri = show.artworkUri,
                )
                status = onLaunchPublishedProgramme(programme).describe()
            }
            FavouriteSource.BBC_IPLAYER -> {
                val episode = bbcIplayerEpisodes.firstOrNull { favouriteKeyForBbc(it.title) == show.key }
                    ?: BbcIplayerEpisode(
                        id = show.bbcEpisodeId ?: return,
                        title = show.title,
                        subtitle = null,
                        artworkUri = show.artworkUri,
                    )
                status = onLaunchBbcIplayerEpisode(episode).describe()
            }
            FavouriteSource.GUIDE -> {
                val airing = show.nextAiring(channels, java.time.Instant.now())
                when {
                    airing == null -> status = "${show.title} isn't in the TV guide right now"
                    airing.onNow || airing.programme.contentUri != null || airing.programme.programmeIntent != null ->
                        launch(airing.channel, airing.programme)
                    else -> {
                        // Not on yet: show when it's on, with the option to set a reminder.
                        details = airing.channel to airing.programme
                        detailsFromFavourites = true
                        showingHome = false
                    }
                }
            }
        }
    }

    // Home live cards open the channel exactly as the guide does (TV tuner for Freeview
    // channels, the app's channel link otherwise), not just the provider app's home screen.
    fun launchFromHome(channel: GuideChannel, programme: io.github.lozza.tellygrid.data.Programme) = launch(channel, programme)

    MaterialTheme(
        typography = TellyGridTypography,
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF70C8FF),
            background = Color(0xFF090D16),
            surface = Color(0xFF151B28),
            onBackground = Color.White,
            onSurface = Color.White,
        ),
    ) {
        val playing = playerTarget
        // Back from the guide returns to TellyGrid's Home rather than leaving the launcher.
        // Screens opened on top of the guide register their own handlers later, so they win.
        BackHandler(enabled = !showingHome) { showingHome = true }
        if (showingHome) {
            if (showingApps) {
                LauncherAppsScreen(
                    installedApps = installedApps,
                    appOrder = launcherAppOrder,
                    onAppOrderChanged = onLauncherAppOrderChanged,
                    watchNextPublishers = publishedRecommendations.watchNext.map { it.packageName }.toSet(),
                    hiddenWatchNextPackages = hiddenWatchNextPackages,
                    onToggleWatchNextApp = onToggleWatchNextApp,
                    onLaunchApp = { app ->
                        status = when (val result = onLaunchInstalledApp(app)) {
                            is LaunchResult.Opened -> "Opening ${result.destination}"
                            is LaunchResult.Failed -> result.message
                        }
                    },
                    onOpenGuide = {
                        showingApps = false
                        showingHome = false
                    },
                    onOpenFavourites = {
                        showingApps = false
                        showingFavourites = true
                        status = null
                    },
                    onOpenSettings = {
                        showingApps = false
                        setupOpenedFromHome = true
                        showingHome = false
                        showingSetup = true
                    },
                    onBack = { showingApps = false },
                )
            } else if (showingFavourites) {
                LauncherFavouritesScreen(
                    favourites = guideSettings.favouriteShows,
                    guideChannels = channels,
                    installedApps = installedApps,
                    status = status,
                    onOpenFavourite = ::openFavourite,
                    onRemoveFavourite = onFavouriteShowChanged,
                    onOpenHome = { showingFavourites = false },
                    onOpenApps = {
                        showingFavourites = false
                        showingApps = true
                    },
                    onOpenGuide = {
                        showingFavourites = false
                        showingHome = false
                    },
                    onOpenSettings = {
                        showingFavourites = false
                        setupOpenedFromHome = true
                        showingHome = false
                        showingSetup = true
                    },
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
                    bbcIplayerEpisodes = bbcIplayerEpisodes,
                    onOpenBbcIplayerEpisode = { episode ->
                        status = when (val result = onLaunchBbcIplayerEpisode(episode)) {
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
                    hiddenWatchNextPackages = hiddenWatchNextPackages,
                    favouriteKeys = favouriteKeys,
                    onToggleFavourite = onFavouriteShowChanged,
                    onOpenFavourites = {
                        status = null
                        showingFavourites = true
                    },
                )
            }
        } else if (details != null) {
            val (channel, programme) = details!!
            ProgrammeDetailsScreen(
                channel = channel,
                programme = programme,
                isFavourite = channel.id in guideSettings.favouriteChannelIds,
                isFavouriteShow = programme.asFavourite(channel).key in favouriteKeys,
                onToggleFavouriteShow = { onFavouriteShowChanged(programme.asFavourite(channel)) },
                hasReminder = guideSettings.reminders.any {
                    it.sameProgramme(GuideReminder.forProgramme(channel, programme))
                },
                canRemind = programme.startsAt.isAfter(java.time.Instant.now()),
                notificationsAllowed = notificationsAllowed,
                onWatch = {
                    details = null
                    if (detailsFromFavourites) showingHome = true
                    detailsFromFavourites = false
                    launch(channel, programme)
                },
                onToggleFavourite = { onFavouriteChanged(channel.id) },
                onRequestNotificationPermission = onRequestNotificationPermission,
                onToggleReminder = { onReminderChanged(GuideReminder.forProgramme(channel, programme)) },
                onHideChannel = {
                    details = null
                    onChannelVisibilityChanged(channel.id)
                },
                onBack = {
                    details = null
                    if (detailsFromFavourites) showingHome = true
                    detailsFromFavourites = false
                },
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
            key(settingsSession) { ProviderSetupScreen(
                providers = providers,
                installedApps = installedApps,
                suggestedApps = suggestedApps,
                selections = selections,
                channels = channels,
                guideSettings = guideSettings,
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
            ) }
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
                        showingFavourites = false
                        showingHome = true
                    },
                    onOpenFavourites = {
                        details = null
                        showingFilter = false
                        showingSearch = false
                        showingSetup = false
                        showingApps = false
                        showingFavourites = true
                        status = null
                        showingHome = true
                    },
                    favouriteShowKeys = favouriteKeys,
                    onToggleFavouriteShow = { channel, programme -> onFavouriteShowChanged(programme.asFavourite(channel)) },
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
