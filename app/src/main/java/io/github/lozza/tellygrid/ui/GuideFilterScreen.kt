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
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF21162E), Color(0xFF4B3159), Color(0xFF21162E))))
            .padding(horizontal = 50.dp, vertical = 28.dp),
    ) {
        Text("GUIDE FILTERS", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 25.sp, letterSpacing = 1.sp)
        Text(
            "Choose a category and optionally a provider. Both filters apply together.",
            color = Color(0xFFCDBFDB), fontSize = 13.sp,
        )
        Spacer(Modifier.height(22.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    title = filter.label.lowercase().replaceFirstChar { it.titlecase() },
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
private fun FilterSection(title: String) {
    Text(title, color = Color(0xFFE0B5FF), fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun FilterRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    requestInitialFocus: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (requestInitialFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    Row(
        Modifier.fillMaxWidth()
            .height(60.dp)
            .background(
                when {
                    focused -> Color(0xFF8D294D)
                    selected -> Color(0xFF50345E)
                    else -> Color(0xFF35263F)
                },
                RoundedCornerShape(4.dp),
            )
            .then(if (requestInitialFocus) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (detail.isNotBlank()) Text(detail, color = Color(0xFFCDBFDB), fontSize = 12.sp)
        }
        Text(if (selected) "SELECTED" else "SELECT", color = if (focused) Color.White else Color(0xFFCDBFDB), fontSize = 11.sp)
    }
}
