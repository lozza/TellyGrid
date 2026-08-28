package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.data.ProviderApp
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.playback.InstalledTvApp

@Composable
fun ProviderSetupScreen(
    providers: List<ProviderApp>,
    installedApps: List<InstalledTvApp>,
    suggestedApps: Map<ProviderId, List<InstalledTvApp>>,
    selections: Map<ProviderId, String>,
    onSelect: (ProviderId, String?) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<ProviderApp?>(null) }
    BackHandler {
        if (editing != null) editing = null else onBack()
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 36.dp, vertical = 26.dp),
    ) {
        Text("TELLYGRID", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            if (editing == null) "App setup" else "Choose app for ${editing!!.displayName}",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
        )
        Text(
            if (editing == null) "Match each service to the app supplied with this TV. Press Back when finished."
            else "Automatic is recommended. Choose a specific app if your Freeview Play version is not detected.",
            color = Color(0xFF9AA6B6),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(22.dp))

        if (editing == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(providers, key = { _, provider -> provider.id }) { index, provider ->
                    val selectedPackage = selections[provider.id]
                    val selectedLabel = installedApps.firstOrNull { it.packageName == selectedPackage }?.label
                    val automatic = suggestedApps[provider.id]?.firstOrNull()?.label
                    SetupRow(
                        title = provider.displayName,
                        detail = selectedLabel?.let { "$it (chosen)" }
                            ?: automatic?.let { "$it (automatic)" }
                            ?: "Not detected — choose the preinstalled app",
                        onClick = { editing = provider },
                        requestInitialFocus = index == 0,
                    )
                }
            }
        } else {
            val provider = editing!!
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    SetupRow(
                        title = "Automatic (recommended)",
                        detail = suggestedApps[provider.id]?.joinToString { it.label } ?: "No automatic match found",
                        onClick = {
                            onSelect(provider.id, null)
                            editing = null
                        },
                        requestInitialFocus = true,
                    )
                }
                items(installedApps, key = { it.packageName }) { app ->
                    SetupRow(
                        title = app.label,
                        detail = app.packageName + if (selections[provider.id] == app.packageName) "  •  selected" else "",
                        onClick = {
                            onSelect(provider.id, app.packageName)
                            editing = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    requestInitialFocus: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (requestInitialFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    val background by animateColorAsState(
        if (focused) Color(0xFF263246) else MaterialTheme.colorScheme.surface,
        label = "setup row background",
    )
    Row(
        Modifier.fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Color(0xFF9AA6B6), fontSize = 12.sp)
        }
        Text("SELECT", color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFF9AA6B6), fontSize = 11.sp)
    }
}
