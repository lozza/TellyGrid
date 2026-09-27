package io.github.lozza.tellygrid

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.app.role.RoleManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.lozza.tellygrid.data.SampleGuideRepository
import io.github.lozza.tellygrid.data.SkyGuideRepository
import io.github.lozza.tellygrid.data.ProviderRegistry
import io.github.lozza.tellygrid.data.GuideViewModel
import io.github.lozza.tellygrid.data.TvPublishedRecommendations
import io.github.lozza.tellygrid.data.BbcIplayerCatalogueRepository
import io.github.lozza.tellygrid.data.BbcIplayerEpisode
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.data.TvPublishedRecommendationsRepository
import io.github.lozza.tellygrid.playback.AppHandoffLauncher
import io.github.lozza.tellygrid.playback.DiscoveryChannelAccessibilityService
import io.github.lozza.tellygrid.playback.NativeTvChannelLauncher
import io.github.lozza.tellygrid.playback.ReminderScheduler
import io.github.lozza.tellygrid.playback.TvAppDiscovery
import io.github.lozza.tellygrid.ui.UnifiedGuideApp
import io.github.lozza.tellygrid.ui.AccessibilityDisclosureScreen

private const val TV_LISTINGS_PERMISSION = "android.permission.READ_TV_LISTINGS"

class MainActivity : ComponentActivity() {
    private val discoveryAccessibilityEnabled = mutableStateOf(false)
    private val recommendationsRefresh = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = AppHandoffLauncher(this)
        val nativeTv = NativeTvChannelLauncher(this)
        val discovery = TvAppDiscovery(this)
        val discoveryGestureSupported =
            DiscoveryChannelAccessibilityService.isGestureInjectionSupported(this)
        val sampleChannels = SampleGuideRepository().channels()

        discoveryAccessibilityEnabled.value = DiscoveryChannelAccessibilityService.isEnabled(this)

        setContent {
            val guideViewModel: GuideViewModel = viewModel()
            val guideSettings by guideViewModel.settings.collectAsState()
            var notificationsAllowed by remember {
                mutableStateOf(ReminderScheduler.notificationsAllowed(this@MainActivity))
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                notificationsAllowed = ReminderScheduler.notificationsAllowed(this@MainActivity)
            }
            var channels by remember { mutableStateOf(sampleChannels) }
            var guideStatus by remember { mutableStateOf("Loading Sky listings…") }
            var publishedRecommendations by remember { mutableStateOf(TvPublishedRecommendations()) }
            var bbcIplayerEpisodes by remember { mutableStateOf(emptyList<BbcIplayerEpisode>()) }
            var tvListingsAllowed by remember {
                mutableStateOf(checkSelfPermission(TV_LISTINGS_PERMISSION) == PackageManager.PERMISSION_GRANTED)
            }
            val tvListingsPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                tvListingsAllowed = granted
                recommendationsRefresh.value++
            }
            val suggestedApps = remember { ProviderRegistry.all.associate { it.id to discovery.suggestedApps(it) } }
            val installedApps = remember {
                (discovery.installedApps() + suggestedApps.values.flatten())
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
            val selections = guideSettings.providerSelections

            LaunchedEffect(Unit) {
                runCatching {
                    withContext(Dispatchers.IO) { SkyGuideRepository(this@MainActivity).load() }
                }.onSuccess { result ->
                    channels = result.channels
                    guideStatus = result.status
                }.onFailure {
                    guideStatus = "Sky listings unavailable — showing demo data"
                }
            }

            LaunchedEffect(tvListingsAllowed, recommendationsRefresh.value) {
                publishedRecommendations = if (tvListingsAllowed) {
                    withContext(Dispatchers.IO) {
                        TvPublishedRecommendationsRepository(this@MainActivity).load()
                    }
                } else TvPublishedRecommendations(loaded = true)
            }

            LaunchedEffect(recommendationsRefresh.value) {
                val bbcInstalled = ProviderRegistry.bbc.packageCandidates.any { packageName ->
                    installedApps.any { it.packageName == packageName }
                }
                if (bbcInstalled) {
                    val episodes = withContext(Dispatchers.IO) {
                        BbcIplayerCatalogueRepository(this@MainActivity).load()
                    }
                    if (episodes.isNotEmpty()) bbcIplayerEpisodes = episodes
                }
            }

            LaunchedEffect(guideSettings.reminders, notificationsAllowed) {
                guideViewModel.reconcileReminders(notificationsAllowed)
            }

            if (
                guideSettings.legacyMigrationComplete &&
                discoveryGestureSupported &&
                !guideSettings.hasSeenDiscoveryAccessibilityDisclosure
            ) {
                AccessibilityDisclosureScreen(
                    onContinue = {
                        guideViewModel.markDiscoveryAccessibilityDisclosureSeen()
                    },
                    onNotNow = {
                        guideViewModel.markDiscoveryAccessibilityDisclosureSeen()
                    },
                )
            } else {
                UnifiedGuideApp(
                    channels = channels,
                    guideStatus = guideStatus,
                    onHandoff = { target -> launcher.launch(target, selections[target.provider.id]) },
                    onLaunchInstalledApp = launcher::launch,
                    onLaunchPublishedProgramme = launcher::launch,
                    publishedRecommendations = publishedRecommendations,
                    bbcIplayerEpisodes = bbcIplayerEpisodes,
                    onLaunchBbcIplayerEpisode = { episode ->
                        launcher.launch(episode, selections[ProviderId.BBC_IPLAYER])
                    },
                    tvListingsAllowed = tvListingsAllowed,
                    onRequestTvListingsPermission = {
                        tvListingsPermissionLauncher.launch(TV_LISTINGS_PERMISSION)
                    },
                    onOpenHomeSettings = {
                        openHomeRoleSettings()
                    },
                    onEnableHomeOverride = {
                        openHomeOverrideSettings()
                    },
                    onOpenAndroidSettings = ::openAndroidSettings,
                    launcherAppOrder = guideSettings.launcherAppOrder,
                    onLauncherAppOrderChanged = guideViewModel::setLauncherAppOrder,
                    hiddenWatchNextPackages = guideSettings.hiddenWatchNextPackages,
                    onToggleWatchNextApp = guideViewModel::toggleWatchNextApp,
                    onResolveNativeChannel = nativeTv::resolve,
                    onNativeChannelFallback = { channel ->
                        nativeTv.launchExternal(channel)
                            ?: io.github.lozza.tellygrid.playback.LaunchResult.Failed(
                                "The TV tuner could not open Freeview channel ${channel.lcn}",
                            )
                    },
                    providers = ProviderRegistry.all,
                    installedApps = installedApps,
                    suggestedApps = suggestedApps,
                    selections = selections,
                    guideSettings = guideSettings,
                    discoveryAccessibilitySupported = discoveryGestureSupported,
                    discoveryAccessibilityEnabled = discoveryAccessibilityEnabled.value,
                    onProviderSelected = { providerId, packageName ->
                        guideViewModel.selectProvider(providerId, packageName)
                    },
                    onProviderEnabledChanged = guideViewModel::toggleProviderEnabled,
                    onGuideFilterChanged = guideViewModel::setFilter,
                    onGuideProviderFilterChanged = guideViewModel::setProviderFilter,
                    onClearGuideFilters = guideViewModel::clearFilters,
                    onGuideSearchResult = guideViewModel::openSearchResult,
                    onGuideTimelineChanged = guideViewModel::setTimelineStart,
                    onGuideSelectionChanged = guideViewModel::setSelection,
                    onFavouriteChanged = guideViewModel::toggleFavourite,
                    notificationsAllowed = notificationsAllowed,
                    onRequestNotificationPermission = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onReminderChanged = { reminder ->
                        guideViewModel.toggleReminder(reminder, notificationsAllowed)
                    },
                    onChannelVisibilityChanged = guideViewModel::toggleHidden,
                    onChannelOrderChanged = guideViewModel::setOrder,
                    onResetChannelOrder = { guideViewModel.setOrder(emptyList()) },
                    onGuideDensityChanged = guideViewModel::setDensity,
                    onResetChannelManagement = guideViewModel::resetChannelManagement,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        discoveryAccessibilityEnabled.value = DiscoveryChannelAccessibilityService.isEnabled(this)
        recommendationsRefresh.value++
    }

    private fun openHomeRoleSettings() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), 4101)
                return
            }
        }
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun openAndroidSettings(): Boolean {
        val intents = listOfNotNull(
            Intent(Settings.ACTION_SETTINGS),
            packageManager.getLeanbackLaunchIntentForPackage("com.android.tv.settings"),
            packageManager.getLaunchIntentForPackage("com.android.tv.settings"),
        )
        return intents.any { intent -> runCatching { startActivity(intent) }.isSuccess }
    }

    private fun openHomeOverrideSettings() {
        val intents: List<Intent> = listOf(
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                data = Uri.parse("package:$packageName")
            },
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        intents.firstOrNull { intent ->
            packageManager.resolveActivity(intent, 0) != null
        }?.let(::startActivity)
    }

}
