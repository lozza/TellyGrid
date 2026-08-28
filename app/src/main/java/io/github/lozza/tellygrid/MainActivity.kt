package io.github.lozza.tellygrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.lozza.tellygrid.data.SampleGuideRepository
import io.github.lozza.tellygrid.data.SkyGuideRepository
import io.github.lozza.tellygrid.data.ProviderRegistry
import io.github.lozza.tellygrid.playback.AppHandoffLauncher
import io.github.lozza.tellygrid.playback.NativeTvChannelLauncher
import io.github.lozza.tellygrid.playback.ProviderPreferences
import io.github.lozza.tellygrid.playback.TvAppDiscovery
import io.github.lozza.tellygrid.ui.UnifiedGuideApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = AppHandoffLauncher(this)
        val nativeTv = NativeTvChannelLauncher(this)
        val discovery = TvAppDiscovery(this)
        val providerPreferences = ProviderPreferences(this)
        val sampleChannels = SampleGuideRepository().channels()

        setContent {
            var channels by remember { mutableStateOf(sampleChannels) }
            var guideStatus by remember { mutableStateOf("Loading Sky listings…") }
            var selectionVersion by remember { mutableStateOf(0) }
            val suggestedApps = remember { ProviderRegistry.all.associate { it.id to discovery.suggestedApps(it) } }
            val installedApps = remember {
                (discovery.installedApps() + suggestedApps.values.flatten())
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
            val selections = remember(selectionVersion) {
                ProviderRegistry.all.mapNotNull { provider ->
                    providerPreferences.selectedPackage(provider.id)?.let { provider.id to it }
                }.toMap()
            }

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

            UnifiedGuideApp(
                channels = channels,
                guideStatus = guideStatus,
                onHandoff = launcher::launch,
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
                onProviderSelected = { providerId, packageName ->
                    providerPreferences.select(providerId, packageName)
                    selectionVersion += 1
                },
            )
        }
    }
}
