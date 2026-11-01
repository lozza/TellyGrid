package io.github.lozza.tellygrid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.app.role.RoleManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import io.github.lozza.tellygrid.playback.HomeOverrideAccessibilityService
import io.github.lozza.tellygrid.playback.NativeTvChannelLauncher
import io.github.lozza.tellygrid.playback.ReminderScheduler
import io.github.lozza.tellygrid.playback.TvAppDiscovery
import io.github.lozza.tellygrid.ui.UnifiedGuideApp
import io.github.lozza.tellygrid.ui.AccessibilityDisclosureScreen
import io.github.lozza.tellygrid.ui.HomeOverrideSetupScreen

private const val TV_LISTINGS_PERMISSION = "android.permission.READ_TV_LISTINGS"

class MainActivity : ComponentActivity() {
    private val discoveryAccessibilityEnabled = mutableStateOf(false)
    private val recommendationsRefresh = mutableStateOf(0)
    private val homePresses = mutableStateOf(0)
    private val homeOverrideEnabled = mutableStateOf(true)
    private val installedAppsRefresh = mutableStateOf(0)
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            installedAppsRefresh.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = AppHandoffLauncher(this)
        val nativeTv = NativeTvChannelLauncher(this)
        val discovery = TvAppDiscovery(this)
        val discoveryGestureSupported =
            DiscoveryChannelAccessibilityService.isGestureInjectionSupported(this)
        val sampleChannels = SampleGuideRepository().channels()

        discoveryAccessibilityEnabled.value = DiscoveryChannelAccessibilityService.isEnabled(this)
        homeOverrideEnabled.value = HomeOverrideAccessibilityService.isEnabled(this)

        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            val appsVersion = installedAppsRefresh.value
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
            val suggestedApps = remember(appsVersion) { ProviderRegistry.all.associate { it.id to discovery.suggestedApps(it) } }
            val installedApps = remember(appsVersion) {
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

            if (!homeOverrideEnabled.value) {
                // Required setup: without the Home override the remote's Home button opens the
                // stock launcher. Shown on first install and whenever the TV turns it off again.
                HomeOverrideSetupScreen(
                    onOpenAccessibilitySettings = ::openHomeOverrideSettings,
                    onCheckAgain = ::refreshHomeOverrideEnabled,
                )
            } else if (
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
                        Unit
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
                    onFavouriteShowChanged = guideViewModel::toggleFavouriteShow,
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
                    homePresses = homePresses.value,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.value++
    }

    override fun onResume() {
        super.onResume()
        discoveryAccessibilityEnabled.value = DiscoveryChannelAccessibilityService.isEnabled(this)
        refreshHomeOverrideEnabled()
        recommendationsRefresh.value++
        installedAppsRefresh.value++
    }

    override fun onDestroy() {
        unregisterReceiver(packageChangeReceiver)
        super.onDestroy()
    }

    private fun refreshHomeOverrideEnabled(): Boolean =
        HomeOverrideAccessibilityService.isEnabled(this).also { homeOverrideEnabled.value = it }

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

    /**
     * Opens the screen where the Home override is switched on. TV makers wire Settings up
     * differently: the service's own details page needs a system-only permission, and on
     * Google TV builds (e.g. Philips) the standard Accessibility action opens an empty stub
     * while the real page sits behind ACCESSIBILITY_TV_OEM_LINK. So try each route in turn,
     * skip stubs, and stop at the first one that actually starts.
     */
    private fun openHomeOverrideSettings(): Boolean {
        val intents = listOfNotNull(
            Intent("android.settings.ACCESSIBILITY_TV_OEM_LINK"),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
            packageManager.getLeanbackLaunchIntentForPackage("com.android.tv.settings"),
            packageManager.getLaunchIntentForPackage("com.android.tv.settings"),
        )
        return intents.any { intent ->
            val target = packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
            target != null && target !in SETTINGS_STUB_PACKAGES &&
                runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }

    private companion object {
        /** Placeholder activities some TV builds register for Settings actions they don't support. */
        val SETTINGS_STUB_PACKAGES = setOf("com.google.android.tv.frameworkpackagestubs")
    }

}
