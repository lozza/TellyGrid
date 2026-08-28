package io.github.lozza.tellygrid.playback

import android.content.Context
import io.github.lozza.tellygrid.data.ProviderId

class ProviderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("provider_apps", Context.MODE_PRIVATE)

    fun selectedPackage(providerId: ProviderId): String? =
        preferences.getString(providerId.name, null)

    fun select(providerId: ProviderId, packageName: String?) {
        preferences.edit().apply {
            if (packageName == null) remove(providerId.name) else putString(providerId.name, packageName)
        }.apply()
    }
}
