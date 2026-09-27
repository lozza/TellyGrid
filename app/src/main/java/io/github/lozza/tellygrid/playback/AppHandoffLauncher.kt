package io.github.lozza.tellygrid.playback

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.ProviderProgrammeIntent
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.data.BbcIplayerEpisode
import io.github.lozza.tellygrid.data.BbcIplayerProgrammeLink
import io.github.lozza.tellygrid.data.ProviderRegistry
import io.github.lozza.tellygrid.data.PrimeVideoProgrammeLink
import io.github.lozza.tellygrid.data.TvPublishedProgramme

sealed interface LaunchResult {
    data class Opened(val destination: String) : LaunchResult
    data class Failed(val message: String) : LaunchResult
}

class AppHandoffLauncher(private val context: Context) {
    private val discovery = TvAppDiscovery(context)

    private companion object {
        const val PLUTO_TV_PACKAGE = "tv.pluto.android"
        const val PLUTO_TV_ENTRY_POINT = "tv.pluto.android.EntryPoint"
    }

    /**
     * Selection is supplied from the immutable app state rather than read from a
     * second preference store. This keeps routing deterministic and testable.
     */
    fun launch(target: PlaybackTarget.ProviderHandoff, selectedPackage: String? = null): LaunchResult {
        val provider = target.provider

        val packages = buildList {
            selectedPackage?.let(::add)
            addAll(provider.packageCandidates)
            addAll(discovery.suggestedApps(provider).map { it.packageName })
        }.distinct()

        // Exact provider-issued links take precedence over live-channel fallbacks.
        val verifiedProgrammeIntent = target.programmeIntent
            ?: target.contentUri?.let(PrimeVideoProgrammeLink::intentOrNull)
            ?: target.contentUri
                ?.takeIf { provider.id == ProviderId.BBC_IPLAYER }
                ?.let(BbcIplayerProgrammeLink::intentOrNull)
        verifiedProgrammeIntent
            ?.takeIf { it.isComplete() && it.matchesSelectedPackage(selectedPackage) }
            ?.takeIf { it.providerPackageName in packages }
            ?.let { programmeIntent ->
                if (tryStart(programmeIntent.toIntent())) {
                    return LaunchResult.Opened("${provider.displayName} episode")
                }
            }

        target.contentUri?.let { uri ->
            packages.forEach { packageName ->
                if (!discovery.canHandle(packageName, uri)) return@forEach
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    setPackage(packageName)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStart(intent)) return LaunchResult.Opened("${provider.displayName} episode")
            }
        }

        // discovery+ exposes no stable live-channel URLs. If the user has
        // explicitly enabled TellyGrid's accessibility service, open its
        // verified Home route and perform the short Browse/channel gesture path.
        if (provider.id == ProviderId.DISCOVERY_PLUS &&
            DiscoveryChannelAccessibilityService.isGestureInjectionSupported(context) &&
            DiscoveryChannelAccessibilityService.isEnabled(context) &&
            DiscoveryChannelAutomation.PACKAGE_NAME in packages &&
            discovery.canHandle(DiscoveryChannelAutomation.PACKAGE_NAME, DiscoveryChannelAutomation.HOME_URI) &&
            DiscoveryChannelAccessibilityService.queueChannel(target.channelId)
        ) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DiscoveryChannelAutomation.HOME_URI)).apply {
                setPackage(DiscoveryChannelAutomation.PACKAGE_NAME)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(intent)) {
                val destination = if (DiscoveryChannelAutomation.hasDirectGesture(target.channelId)) {
                    "${provider.displayName} ${target.channelId.replace('-', ' ')}"
                } else {
                    "${provider.displayName} Browse"
                }
                return LaunchResult.Opened(destination)
            }
            DiscoveryChannelAccessibilityService.cancelQueuedChannel()
        }

        // Pluto's Android TV manifest does not claim its public web route, but
        // its exported EntryPoint accepts the same route when started explicitly.
        // This was verified with the COPS channel on the reference Philips TV.
        if (
            provider.id == ProviderId.PLUTO_TV &&
            (selectedPackage == null || selectedPackage == PLUTO_TV_PACKAGE) &&
            PLUTO_TV_PACKAGE in packages
        ) {
            target.channelUri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    component = ComponentName(PLUTO_TV_PACKAGE, PLUTO_TV_ENTRY_POINT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStart(intent)) return LaunchResult.Opened("${provider.displayName} live channel")
            }
        }

        // Only send a channel link to an installed app that declares it can handle it.
        target.channelUri?.let { uri ->
            packages.forEach { packageName ->
                if (!discovery.canHandle(packageName, uri)) return@forEach
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    setPackage(packageName)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStart(intent)) return LaunchResult.Opened("${provider.displayName} live channel")
            }
        }

        // Fallback to the installed provider app. Never redirect a Freeview Play TV
        // to a retail Play Store build that may be incompatible with the device.
        packages.forEach { packageName ->
            context.packageManager.getLeanbackLaunchIntentForPackage(packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (tryStart(intent)) return LaunchResult.Opened(provider.displayName)
            }
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (tryStart(intent)) return LaunchResult.Opened(provider.displayName)
            }
        }

        return LaunchResult.Failed("No ${provider.displayName} app is mapped. Open App setup to choose the preinstalled TV app.")
    }

    fun launch(app: InstalledTvApp): LaunchResult {
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return LaunchResult.Failed("${app.label} cannot be opened on this TV")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(intent)) LaunchResult.Opened(app.label) else LaunchResult.Failed("Could not open ${app.label}")
    }

    /**
     * Open a BBC iPlayer catalogue episode. Freeview Play TVs get the verified AIT
     * request; retail iPlayer gets the public episode link if it claims it. If
     * neither route is available the iPlayer app opens at its home screen.
     */
    fun launch(episode: BbcIplayerEpisode, selectedPackage: String? = null): LaunchResult {
        val provider = ProviderRegistry.bbc
        val installed = buildList {
            selectedPackage?.let(::add)
            addAll(provider.packageCandidates)
        }.distinct().filter(::isInstalled)

        BbcIplayerProgrammeLink.intentOrNull(BbcIplayerProgrammeLink.aitUrlForEpisode(episode.id))
            ?.takeIf { it.matchesSelectedPackage(selectedPackage) && it.providerPackageName in installed }
            ?.let { if (tryStart(it.toIntent())) return LaunchResult.Opened(episode.title) }

        BbcIplayerProgrammeLink.webUrlForEpisode(episode.id)?.let { uri ->
            installed.filter { it != BbcIplayerProgrammeLink.PROVIDER_PACKAGE_NAME }.forEach { packageName ->
                if (!discovery.canHandle(packageName, uri)) return@forEach
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    setPackage(packageName)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStart(intent)) return LaunchResult.Opened(episode.title)
            }
        }

        installed.forEach { packageName ->
            val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?: context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return@forEach
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStart(intent)) return LaunchResult.Opened("${provider.displayName} (this TV has no direct episode link)")
        }
        return LaunchResult.Failed("BBC iPlayer is not installed on this TV")
    }

    private fun isInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    /** Launch the provider-issued intent carried by an Android TV Home card. */
    fun launch(programme: TvPublishedProgramme): LaunchResult {
        val intent = runCatching { Intent.parseUri(programme.intentUri, Intent.URI_INTENT_SCHEME) }
            .getOrNull() ?: return LaunchResult.Failed("Invalid TV recommendation link")
        if (programme.packageName.isBlank() ||
            intent.component?.packageName?.let { it != programme.packageName } == true ||
            intent.`package`?.let { it != programme.packageName } == true ||
            intent.selector != null
        ) return LaunchResult.Failed("Recommendation does not target its provider app")
        intent.setPackage(programme.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(intent)) LaunchResult.Opened(programme.title)
        else LaunchResult.Failed("Could not open ${programme.title}")
    }

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

}

private fun ProviderProgrammeIntent.toIntent(): Intent = Intent(action).apply {
    setPackage(packageName)
    componentClassName?.let { component = ComponentName(packageName, it) }
    dataUri?.let { data = Uri.parse(it) }
    mimeType?.let { type = it }
    categories.forEach(::addCategory)
    stringExtras.forEach { (key, value) -> putExtra(key, value) }
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
