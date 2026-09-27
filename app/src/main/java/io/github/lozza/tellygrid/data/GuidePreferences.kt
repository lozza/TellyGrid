package io.github.lozza.tellygrid.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import java.io.IOException

private val Context.guideDataStore by preferencesDataStore(name = "tellygrid_guide")

enum class GuideFilter(val label: String) {
    ALL("ALL CHANNELS"),
    FAVOURITES("FAVOURITES"),
    FREEVIEW("FREEVIEW"),
    // Persist the stable FAST enum name, but describe the currently curated
    // provider instead of exposing opaque implementation jargon.
    FAST("PLUTO TV"),
    ENTERTAINMENT("ENTERTAINMENT"),
    MOVIES("MOVIES"),
    SPORT("SPORT"),
    KIDS("KIDS"),
    NEWS("NEWS"),
    DOCUMENTARY("DOCUMENTARY"),
}

enum class GuideDensity { COMFORTABLE, COMPACT }

/** A stable, account-free local reminder identity. */
data class GuideReminder(
    val channelId: String,
    val programmeId: String,
    val startsAtEpochMillis: Long,
    /** Display data is local-only, so a scheduled alert remains understandable offline. */
    val channelName: String = "",
    val programmeTitle: String = "",
) {
    companion object {
        fun forProgramme(channel: GuideChannel, programme: Programme) = GuideReminder(
            channelId = channel.id,
            programmeId = programme.id,
            startsAtEpochMillis = programme.startsAt.toEpochMilli(),
            channelName = channel.name,
            programmeTitle = programme.title,
        )
    }
}

data class GuideSettings(
    val favouriteChannelIds: Set<String> = emptySet(),
    val hiddenChannelIds: Set<String> = emptySet(),
    val channelOrder: List<String> = emptyList(),
    val filter: GuideFilter = GuideFilter.ALL,
    val providerFilter: ProviderId? = null,
    val disabledProviderIds: Set<ProviderId> = emptySet(),
    val timelineStartEpochMillis: Long? = null,
    val selectedChannelId: String? = null,
    val selectedProgrammeId: String? = null,
    val density: GuideDensity = GuideDensity.COMFORTABLE,
    val providerSelections: Map<ProviderId, String> = emptyMap(),
    val launcherAppOrder: List<String> = emptyList(),
    /** Apps whose Watch Next entries are hidden from Home. New apps show by default. */
    val hiddenWatchNextPackages: Set<String> = emptySet(),
    val hasSeenDiscoveryAccessibilityDisclosure: Boolean = false,
    val legacyMigrationComplete: Boolean = false,
    val reminders: Set<GuideReminder> = emptySet(),
)

/**
 * Device-local, account-free guide preferences. Provider credentials and playback
 * state intentionally never enter this store.
 */
class GuidePreferences(private val context: Context) {
    private object Keys {
        val favourites = stringSetPreferencesKey("favourite_channel_ids")
        val hidden = stringSetPreferencesKey("hidden_channel_ids")
        val order = stringPreferencesKey("channel_order")
        val filter = stringPreferencesKey("filter")
        val providerFilter = stringPreferencesKey("provider_filter")
        val disabledProviders = stringSetPreferencesKey("disabled_provider_ids")
        val timelineStart = longPreferencesKey("timeline_start_epoch_ms")
        val selectedChannel = stringPreferencesKey("selected_channel_id")
        val selectedProgramme = stringPreferencesKey("selected_programme_id")
        val density = stringPreferencesKey("density")
        val discoveryAccessibilityDisclosure = booleanPreferencesKey("discovery_accessibility_disclosure_seen")
        val legacyMigrationComplete = booleanPreferencesKey("legacy_preferences_migrated_v1")
        val reminders = stringSetPreferencesKey("programme_reminders_v1")
        val launcherAppOrder = stringPreferencesKey("launcher_app_order")
        val hiddenWatchNextPackages = stringSetPreferencesKey("hidden_watch_next_packages")

        fun providerSelection(providerId: ProviderId) =
            stringPreferencesKey("provider_selection_${providerId.name.lowercase()}")
    }

    val settings: Flow<GuideSettings> = context.guideDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            GuideSettings(
                favouriteChannelIds = values[Keys.favourites].orEmpty(),
                hiddenChannelIds = values[Keys.hidden].orEmpty(),
                channelOrder = values[Keys.order]?.split('|')?.filter(String::isNotBlank).orEmpty(),
                filter = values[Keys.filter].enumOrDefault(GuideFilter.ALL),
                providerFilter = values[Keys.providerFilter].providerOrNull(),
                disabledProviderIds = values[Keys.disabledProviders].orEmpty().mapNotNull {
                    runCatching { enumValueOf<ProviderId>(it) }.getOrNull()
                }.toSet(),
                timelineStartEpochMillis = values[Keys.timelineStart],
                selectedChannelId = values[Keys.selectedChannel],
                selectedProgrammeId = values[Keys.selectedProgramme],
                density = values[Keys.density].enumOrDefault(GuideDensity.COMFORTABLE),
                providerSelections = ProviderId.values().mapNotNull { providerId ->
                    values[Keys.providerSelection(providerId)]?.let { providerId to it }
                }.toMap(),
                launcherAppOrder = values[Keys.launcherAppOrder]
                    ?.split('|')?.filter(String::isNotBlank).orEmpty(),
                hiddenWatchNextPackages = values[Keys.hiddenWatchNextPackages].orEmpty(),
                hasSeenDiscoveryAccessibilityDisclosure =
                    values[Keys.discoveryAccessibilityDisclosure] ?: false,
                legacyMigrationComplete = values[Keys.legacyMigrationComplete] ?: false,
                reminders = values[Keys.reminders].orEmpty().mapNotNull(::decodeReminder).toSet(),
            )
        }

    /**
     * One-way, idempotent import of the two pre-DataStore preference files. The
     * legacy files remain intact so a failed upgrade never erases a TV owner's app
     * choices or disclosure acknowledgement.
     */
    suspend fun migrateLegacyPreferences() = context.guideDataStore.edit { values ->
        if (values[Keys.legacyMigrationComplete] == true) return@edit

        val legacyProviders = context.getSharedPreferences("provider_apps", Context.MODE_PRIVATE)
        val existingSelections = ProviderId.values().mapNotNull { providerId ->
            values[Keys.providerSelection(providerId)]?.let { providerId to it }
        }.toMap()
        val legacySelections = ProviderId.values().mapNotNull { providerId ->
            legacyProviders.getString(providerId.name, null)?.let { providerId to it }
        }.toMap()
        mergeProviderSelections(existingSelections, legacySelections).forEach { (providerId, packageName) ->
            val destination = Keys.providerSelection(providerId)
            values[destination] = packageName
        }

        val legacyDisclosure = context.getSharedPreferences(
            "accessibility_disclosure",
            Context.MODE_PRIVATE,
        ).getBoolean("discovery_channel_control_seen", false)
        values[Keys.discoveryAccessibilityDisclosure] = migratedDisclosure(
            values[Keys.discoveryAccessibilityDisclosure],
            legacyDisclosure,
        )
        values[Keys.legacyMigrationComplete] = true
    }

    suspend fun toggleFavourite(channelId: String) = context.guideDataStore.edit { values ->
        values[Keys.favourites] = values[Keys.favourites].orEmpty().toMutableSet().apply {
            if (!add(channelId)) remove(channelId)
        }
    }

    suspend fun toggleHidden(channelId: String) = context.guideDataStore.edit { values ->
        values[Keys.hidden] = values[Keys.hidden].orEmpty().toMutableSet().apply {
            if (!add(channelId)) remove(channelId)
        }
    }

    suspend fun setFilter(filter: GuideFilter) = context.guideDataStore.edit { it[Keys.filter] = filter.name }

    suspend fun setProviderFilter(providerId: ProviderId?) = context.guideDataStore.edit { values ->
        if (providerId == null) values.remove(Keys.providerFilter) else values[Keys.providerFilter] = providerId.name
    }

    suspend fun toggleProviderEnabled(providerId: ProviderId) = context.guideDataStore.edit { values ->
        values[Keys.disabledProviders] = values[Keys.disabledProviders].orEmpty().toMutableSet().apply {
            if (!add(providerId.name)) remove(providerId.name)
        }
    }

    suspend fun clearFilters() = context.guideDataStore.edit { values ->
        values[Keys.filter] = GuideFilter.ALL.name
        values.remove(Keys.providerFilter)
    }

    suspend fun setTimelineStart(epochMillis: Long) = context.guideDataStore.edit {
        it[Keys.timelineStart] = epochMillis
    }

    /** The guide position is session state; a fresh app launch should start on today. */
    suspend fun clearTimelineStart() = context.guideDataStore.edit {
        it.remove(Keys.timelineStart)
    }

    suspend fun setSelection(channelId: String?, programmeId: String?) = context.guideDataStore.edit { values ->
        if (channelId == null) values.remove(Keys.selectedChannel) else values[Keys.selectedChannel] = channelId
        if (programmeId == null) values.remove(Keys.selectedProgramme) else values[Keys.selectedProgramme] = programmeId
    }

    /** Atomically restores the all-channel grid around a result selected in search. */
    suspend fun openSearchResult(channelId: String, programmeId: String, timelineStart: Long) =
        context.guideDataStore.edit { values ->
            values[Keys.filter] = GuideFilter.ALL.name
            values.remove(Keys.providerFilter)
            values[Keys.timelineStart] = timelineStart
            values[Keys.selectedChannel] = channelId
            values[Keys.selectedProgramme] = programmeId
        }

    suspend fun setDensity(density: GuideDensity) = context.guideDataStore.edit { it[Keys.density] = density.name }

    /** Returns true when the record was added, false when it was cancelled. */
    suspend fun toggleReminder(reminder: GuideReminder): Boolean {
        var added = false
        context.guideDataStore.edit { values ->
            values[Keys.reminders] = values[Keys.reminders].orEmpty().toMutableSet().apply {
                val existing = firstOrNull { encoded ->
                    decodeReminder(encoded)?.sameProgramme(reminder) == true
                }
                added = existing == null
                if (existing == null) add(encodeReminder(reminder)) else remove(existing)
            }
        }
        return added
    }

    suspend fun selectProvider(providerId: ProviderId, packageName: String?) = context.guideDataStore.edit { values ->
        val key = Keys.providerSelection(providerId)
        if (packageName == null) values.remove(key) else values[key] = packageName
    }

    suspend fun setLauncherAppOrder(order: List<String>) = context.guideDataStore.edit {
        it[Keys.launcherAppOrder] = order.distinct().joinToString("|")
    }

    suspend fun toggleWatchNextApp(packageName: String) = context.guideDataStore.edit { values ->
        val hidden = values[Keys.hiddenWatchNextPackages].orEmpty()
        values[Keys.hiddenWatchNextPackages] =
            if (packageName in hidden) hidden - packageName else hidden + packageName
    }

    suspend fun markDiscoveryAccessibilityDisclosureSeen() = context.guideDataStore.edit {
        it[Keys.discoveryAccessibilityDisclosure] = true
    }

    suspend fun setOrder(order: List<String>) = context.guideDataStore.edit {
        it[Keys.order] = order.distinct().joinToString("|")
    }

    suspend fun resetChannelManagement() = context.guideDataStore.edit { values ->
        values.remove(Keys.hidden)
        values.remove(Keys.order)
    }
}

class GuideViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = GuidePreferences(application)
    private val reminderScheduler = io.github.lozza.tellygrid.playback.ReminderScheduler(application)
    private val initialTimelineReset = viewModelScope.async { preferences.clearTimelineStart() }
    val settings = preferences.settings.onStart { initialTimelineReset.await() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GuideSettings(),
    )

    init {
        viewModelScope.launch { preferences.migrateLegacyPreferences() }
    }

    fun toggleFavourite(channelId: String) = viewModelScope.launch { preferences.toggleFavourite(channelId) }
    fun toggleHidden(channelId: String) = viewModelScope.launch { preferences.toggleHidden(channelId) }
    fun setFilter(filter: GuideFilter) = viewModelScope.launch { preferences.setFilter(filter) }
    fun setProviderFilter(providerId: ProviderId?) = viewModelScope.launch {
        preferences.setProviderFilter(providerId)
    }
    fun toggleProviderEnabled(providerId: ProviderId) = viewModelScope.launch {
        preferences.toggleProviderEnabled(providerId)
    }
    fun clearFilters() = viewModelScope.launch { preferences.clearFilters() }
    fun setTimelineStart(epochMillis: Long) = viewModelScope.launch { preferences.setTimelineStart(epochMillis) }
    fun setSelection(channelId: String?, programmeId: String?) = viewModelScope.launch {
        preferences.setSelection(channelId, programmeId)
    }
    fun openSearchResult(channelId: String, programmeId: String, timelineStart: Long) = viewModelScope.launch {
        preferences.openSearchResult(channelId, programmeId, timelineStart)
    }
    fun setDensity(density: GuideDensity) = viewModelScope.launch { preferences.setDensity(density) }
    fun toggleReminder(reminder: GuideReminder, notificationsAllowed: Boolean) = viewModelScope.launch {
        if (preferences.toggleReminder(reminder)) {
            reminderScheduler.schedule(reminder, notificationsAllowed)
        } else {
            reminderScheduler.cancel(reminder)
        }
    }
    fun reconcileReminders(notificationsAllowed: Boolean) = viewModelScope.launch {
        reminderScheduler.reconcile(settings.value.reminders, notificationsAllowed)
    }
    fun setOrder(order: List<String>) = viewModelScope.launch { preferences.setOrder(order) }
    fun selectProvider(providerId: ProviderId, packageName: String?) = viewModelScope.launch {
        preferences.selectProvider(providerId, packageName)
    }
    fun setLauncherAppOrder(order: List<String>) = viewModelScope.launch {
        preferences.setLauncherAppOrder(order)
    }
    fun toggleWatchNextApp(packageName: String) = viewModelScope.launch {
        preferences.toggleWatchNextApp(packageName)
    }
    fun markDiscoveryAccessibilityDisclosureSeen() = viewModelScope.launch {
        preferences.markDiscoveryAccessibilityDisclosureSeen()
    }
    fun resetChannelManagement() = viewModelScope.launch { preferences.resetChannelManagement() }
}

private inline fun <reified T : Enum<T>> String?.enumOrDefault(default: T): T =
    runCatching { enumValueOf<T>(this.orEmpty()) }.getOrDefault(default)

private fun String?.providerOrNull(): ProviderId? =
    runCatching { enumValueOf<ProviderId>(this.orEmpty()) }.getOrNull()

/** Existing DataStore choices always win over values imported from legacy storage. */
internal fun mergeProviderSelections(
    existing: Map<ProviderId, String>,
    legacy: Map<ProviderId, String>,
): Map<ProviderId, String> = legacy + existing

internal fun migratedDisclosure(existing: Boolean?, legacy: Boolean): Boolean = existing ?: legacy

internal fun encodeReminder(reminder: GuideReminder): String = listOf(
    reminder.channelId,
    reminder.programmeId,
    reminder.startsAtEpochMillis.toString(),
    reminder.channelName,
    reminder.programmeTitle,
).joinToString("\t") { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }

internal fun decodeReminder(value: String): GuideReminder? {
    val parts = value.split('\t')
    if (parts.size == 3) {
        // Read records saved by alpha.6; the next interaction upgrades them safely.
        val start = parts.getOrNull(2)?.toLongOrNull() ?: return null
        val channelId = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return null
        val programmeId = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        return GuideReminder(channelId, programmeId, start)
    }
    if (parts.size != 5) return null
    val decoded = runCatching {
        parts.map { String(java.util.Base64.getUrlDecoder().decode(it)) }
    }.getOrNull() ?: return null
    val start = decoded[2].toLongOrNull() ?: return null
    val channelId = decoded[0].takeIf(String::isNotBlank) ?: return null
    val programmeId = decoded[1].takeIf(String::isNotBlank) ?: return null
    return GuideReminder(channelId, programmeId, start, decoded[3], decoded[4])
}

/** Metadata may be refreshed, but a reminder always identifies one programme slot. */
internal fun GuideReminder.sameProgramme(other: GuideReminder): Boolean =
    channelId == other.channelId &&
        programmeId == other.programmeId &&
        startsAtEpochMillis == other.startsAtEpochMillis

fun List<GuideChannel>.forGuideSettings(settings: GuideSettings): List<GuideChannel> {
    val visible = filterNot { channel ->
        channel.id in settings.hiddenChannelIds ||
            ((channel.playback as? PlaybackTarget.ProviderHandoff)?.provider?.id?.let {
                it in settings.disabledProviderIds
            } == true)
    }
    val categoryFiltered = visible.filter { channel ->
        when (settings.filter) {
            GuideFilter.ALL -> true
            GuideFilter.FAVOURITES -> channel.id in settings.favouriteChannelIds
            GuideFilter.FREEVIEW -> (channel.playback as? PlaybackTarget.ProviderHandoff)?.terrestrialLcn != null
            // "PLUTO TV" is a provider view, not a genre. Pluto movie, sport
            // and kids rows must still participate in the matching genre view.
            GuideFilter.FAST ->
                (channel.playback as? PlaybackTarget.ProviderHandoff)?.provider?.id == ProviderId.PLUTO_TV
            GuideFilter.ENTERTAINMENT -> channel.category == GuideCategory.ENTERTAINMENT
            GuideFilter.MOVIES -> channel.category == GuideCategory.MOVIES
            GuideFilter.SPORT -> channel.category == GuideCategory.SPORT
            GuideFilter.KIDS -> channel.category == GuideCategory.KIDS
            GuideFilter.NEWS -> channel.category == GuideCategory.NEWS
            GuideFilter.DOCUMENTARY -> channel.category == GuideCategory.DOCUMENTARY
        }
    }
    val filtered = settings.providerFilter?.let { selectedProvider ->
        categoryFiltered.filter {
            (it.playback as? PlaybackTarget.ProviderHandoff)?.provider?.id == selectedProvider
        }
    } ?: categoryFiltered
    if (settings.channelOrder.isEmpty()) return filtered
    val orderIndex = settings.channelOrder.withIndex().associate { it.value to it.index }
    return filtered.sortedWith(compareBy<GuideChannel> { orderIndex[it.id] ?: Int.MAX_VALUE }.thenBy { it.number })
}
