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
    fun launch(target: PlaybackTarget.ProviderHandoff): LaunchResult {
        val provider = target.provider

        // Prefer a content-level link only after the provider has documented/issued it.
        target.verifiedDeepLinkUri?.let { uri ->
            provider.packageCandidates.forEach { packageName ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStart(intent)) return LaunchResult.Opened(provider.displayName)
            }
        }

        // Reliable MVP fallback: open the provider app at its own home screen.
        provider.packageCandidates.forEach { packageName ->
            context.packageManager.getLeanbackLaunchIntentForPackage(packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (tryStart(intent)) return LaunchResult.Opened(provider.displayName)
            }
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (tryStart(intent)) return LaunchResult.Opened(provider.displayName)
            }
        }

        // If absent, show the TV Play Store listing (or its web equivalent).
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${provider.playStorePackage}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(market)) return LaunchResult.Opened("Google Play")

        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${provider.playStorePackage}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (tryStart(web)) {
            LaunchResult.Opened("Google Play")
        } else {
            LaunchResult.Failed("${provider.displayName} is not installed on this TV.")
        }
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
