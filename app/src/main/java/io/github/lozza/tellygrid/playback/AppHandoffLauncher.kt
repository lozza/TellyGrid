package io.github.lozza.tellygrid.playback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.lozza.tellygrid.data.PlaybackTarget

sealed interface LaunchResult {
    data class Opened(val destination: String) : LaunchResult
    data class Failed(val message: String) : LaunchResult
}

class AppHandoffLauncher(private val context: Context) {
    private val discovery = TvAppDiscovery(context)
    private val preferences = ProviderPreferences(context)

    fun launch(target: PlaybackTarget.ProviderHandoff): LaunchResult {
        val provider = target.provider

        val packages = buildList {
            preferences.selectedPackage(provider.id)?.let(::add)
            addAll(provider.packageCandidates)
            addAll(discovery.suggestedApps(provider).map { it.packageName })
        }.distinct()

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

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
