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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.data.ProviderApp
import io.github.lozza.tellygrid.data.ProviderId
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.GuideDensity
import io.github.lozza.tellygrid.data.GuideSettings
import io.github.lozza.tellygrid.playback.InstalledTvApp

@Composable
fun ProviderSetupScreen(
    providers: List<ProviderApp>,
    installedApps: List<InstalledTvApp>,
    suggestedApps: Map<ProviderId, List<InstalledTvApp>>,
    selections: Map<ProviderId, String>,
    channels: List<GuideChannel>,
    guideSettings: GuideSettings,
    discoveryAccessibilitySupported: Boolean,
    discoveryAccessibilityEnabled: Boolean,
    onSelect: (ProviderId, String?) -> Unit,
    onProviderEnabledChanged: (ProviderId) -> Unit,
    onGuideDensityChanged: (GuideDensity) -> Unit,
    onToggleHidden: (String) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    onResetOrder: () -> Unit,
    onResetChannelManagement: () -> Unit,
    onOpenAndroidSettings: () -> Boolean,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<ProviderApp?>(null) }
    var showingAccessibilityInstructions by remember { mutableStateOf(false) }
    var showingAccessibilityUnsupported by remember { mutableStateOf(false) }
    var showingGuidePreferences by remember { mutableStateOf(false) }
    var androidSettingsError by remember { mutableStateOf(false) }
    BackHandler(enabled = !showingAccessibilityInstructions && !showingAccessibilityUnsupported) {
        if (editing != null) editing = null else onBack()
    }

    if (showingAccessibilityUnsupported) {
        AccessibilityUnsupportedScreen(onBack = { showingAccessibilityUnsupported = false })
        return
    }

    if (showingGuidePreferences) {
        GuidePreferencesScreen(
            channels = channels,
            settings = guideSettings,
            onDensityChanged = onGuideDensityChanged,
            onToggleHidden = onToggleHidden,
            onOrderChanged = onOrderChanged,
            onResetOrder = onResetOrder,
            onResetChannelManagement = onResetChannelManagement,
            onBack = { showingGuidePreferences = false },
        )
        return
    }

    if (showingAccessibilityInstructions) {
        AccessibilityDisclosureScreen(
            onContinue = { showingAccessibilityInstructions = false },
            onNotNow = { showingAccessibilityInstructions = false },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(ScreenBackground).then(ScreenPadding),
    ) {
        Text(
            if (editing == null) "SETTINGS" else "${editing!!.displayName.uppercase()} APP",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 25.sp,
            letterSpacing = 1.sp,
        )
        Text(
            if (editing == null) "Match each service to the app supplied with this TV. Press Back when finished."
            else "Automatic is recommended. Choose a specific app if your Freeview Play version is not detected.",
            color = Color(0xFFCDBFDB),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(22.dp))

        if (androidSettingsError) {
            Text(
                "Android TV settings could not be opened on this device.",
                color = Color(0xFFFFB4B4),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (editing == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
                item {
                    SetupRow(
                        title = "Android TV settings",
                        detail = "Open the TV's system settings",
                        onClick = { androidSettingsError = !onOpenAndroidSettings() },
                        requestInitialFocus = true,
                    )
                }
                item {
                    SetupRow(
                        title = "Guide preferences",
                        detail = "Density, hidden channels and reset controls",
                        onClick = { showingGuidePreferences = true },
                        requestInitialFocus = false,
                    )
                }
                item {
                    SetupRow(
                        title = "Discovery+ direct channels",
                        detail = if (!discoveryAccessibilitySupported) {
                            "Unavailable — this TV blocks accessibility touch gestures"
                        } else if (discoveryAccessibilityEnabled) {
                            "Accessibility control enabled — select to review the manual settings path"
                        } else {
                            "Optional — select to see the manual settings path"
                        },
                        onClick = {
                            if (discoveryAccessibilitySupported) {
                                showingAccessibilityInstructions = true
                            } else {
                                showingAccessibilityUnsupported = true
                            }
                        },
                        requestInitialFocus = false,
                    )
                }
                itemsIndexed(providers, key = { _, provider -> provider.id }) { _, provider ->
                    val selectedPackage = selections[provider.id]
                    val selectedLabel = installedApps.firstOrNull { it.packageName == selectedPackage }?.label
                    val automatic = suggestedApps[provider.id]?.firstOrNull()?.label
                    SetupRow(
                        title = provider.displayName,
                        detail = selectedLabel?.let { "$it (chosen)" }
                            ?: automatic?.let { "$it (automatic)" }
                            ?: "Not detected — choose the preinstalled app",
                        onClick = { editing = provider },
                        requestInitialFocus = false,
                    )
                }
            }
        } else {
            val provider = editing!!
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
                item {
                    val disabled = provider.id in guideSettings.disabledProviderIds
                    SetupRow(
                        title = if (disabled) "Enable ${provider.displayName} in guide" else "Disable ${provider.displayName} in guide",
                        detail = if (disabled) "Hidden rows are kept and can be restored later" else "Hide this provider's rows without deleting app choices",
                        onClick = {
                            onProviderEnabledChanged(provider.id)
                            editing = null
                        },
                        requestInitialFocus = true,
                    )
                }
                item {
                    SetupRow(
                        title = "Automatic (recommended)",
                        detail = suggestedApps[provider.id]?.joinToString { it.label } ?: "No automatic match found",
                        onClick = {
                            onSelect(provider.id, null)
                            editing = null
                        },
                        requestInitialFocus = false,
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
) = SettingsRow(title, detail, "SELECT", onClick, requestInitialFocus = requestInitialFocus)
