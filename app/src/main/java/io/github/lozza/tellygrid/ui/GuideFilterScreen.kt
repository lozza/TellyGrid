package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import io.github.lozza.tellygrid.data.GuideFilter
import io.github.lozza.tellygrid.data.GuideSettings
import io.github.lozza.tellygrid.data.ProviderApp
import io.github.lozza.tellygrid.data.ProviderId

/** D-pad-first category and provider filter editor. Selections combine rather than replace. */
@Composable
fun GuideFilterScreen(
    settings: GuideSettings,
    providers: List<ProviderApp>,
    onFilterSelected: (GuideFilter) -> Unit,
    onProviderSelected: (ProviderId?) -> Unit,
    onClearFilters: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(ScreenBackground).then(ScreenPadding),
    ) {
        ScreenTitle("GUIDE FILTERS", "Choose a category and optionally a provider. Both filters apply together.")
        Spacer(Modifier.height(22.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
            item {
                FilterRow(
                    title = "Clear filters",
                    detail = "Show every available channel",
                    selected = settings.filter == GuideFilter.ALL && settings.providerFilter == null,
                    onClick = onClearFilters,
                    requestInitialFocus = true,
                )
            }
            item { FilterSection("Categories") }
            items(GuideFilter.values().filter { it != GuideFilter.ALL }, key = { it.name }) { filter ->
                FilterRow(
                    title = filter.label.split(' ').joinToString(" ") { word ->
                        if (word.length <= 3) word else word.lowercase().replaceFirstChar { it.titlecase() }
                    },
                    detail = if (settings.filter == filter) "Selected" else "",
                    selected = settings.filter == filter,
                    onClick = { onFilterSelected(filter) },
                )
            }
            item { FilterSection("Providers") }
            item {
                FilterRow(
                    title = "All providers",
                    detail = if (settings.providerFilter == null) "Selected" else "",
                    selected = settings.providerFilter == null,
                    onClick = { onProviderSelected(null) },
                )
            }
            items(providers, key = { it.id }) { provider ->
                val selected = settings.providerFilter == provider.id
                FilterRow(
                    title = provider.displayName,
                    detail = if (selected) "Selected" else "Only this provider",
                    selected = selected,
                    onClick = { onProviderSelected(if (selected) null else provider.id) },
                )
            }
        }
    }
}

@Composable
private fun FilterSection(title: String) = SettingsSection(title)

@Composable
private fun FilterRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    requestInitialFocus: Boolean = false,
) = SettingsRow(title, detail, if (selected) "SELECTED" else "SELECT", onClick, selected, requestInitialFocus)
