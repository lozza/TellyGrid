package io.github.lozza.tellygrid.playback

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.lozza.tellygrid.data.ProviderApp

data class InstalledTvApp(
    val packageName: String,
    val label: String,
)

class TvAppDiscovery(private val context: Context) {
    private val packageManager = context.packageManager

    @Suppress("DEPRECATION")
    fun installedApps(): List<InstalledTvApp> {
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        )
        return intents
            .flatMap { packageManager.queryIntentActivities(it, PackageManager.MATCH_ALL) }
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                InstalledTvApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(packageManager).toString().ifBlank { it.activityInfo.packageName },
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun suggestedApps(provider: ProviderApp): List<InstalledTvApp> {
        val apps = (installedApps() + provider.discoveryUris.flatMap(::appsHandlingUri))
            .distinctBy { it.packageName }
        return apps.filter { app ->
            val searchable = "${app.label} ${app.packageName}".lowercase()
            provider.discoveryTerms.any { searchable.contains(it.lowercase()) }
        }
    }

    @Suppress("DEPRECATION")
    fun appsHandlingUri(uri: String): List<InstalledTvApp> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                InstalledTvApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(packageManager).toString().ifBlank { it.activityInfo.packageName },
                )
            }
            .distinctBy { it.packageName }
    }

    @Suppress("DEPRECATION")
    fun canHandle(packageName: String, uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).isNotEmpty()
    }
}
