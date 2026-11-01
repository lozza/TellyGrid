package io.github.lozza.tellygrid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lozza.tellygrid.playback.ButtonDestination
import io.github.lozza.tellygrid.playback.RemoteButtonCapture
import io.github.lozza.tellygrid.playback.RemoteButtonStore
import io.github.lozza.tellygrid.playback.RemoteButtons

/** One-line summary for the Settings row that opens [RemoteButtonsScreen]. */
@Composable
fun remoteButtonsRowDetail(): String {
    val mappings = RemoteButtonStore.all(LocalContext.current)
    return when (mappings.size) {
        0 -> "Make buttons like Settings or Guide open a TellyGrid page"
        1 -> mappings.entries.first().let { (key, dest) -> "${RemoteButtons.label(key)} opens ${dest.label}" }
        else -> "${mappings.size} buttons set up"
    }
}

private sealed interface RemoteButtonsStep {
    data object List : RemoteButtonsStep
    data object Capturing : RemoteButtonsStep
    data class Choosing(val keyCode: Int) : RemoteButtonsStep
}

/**
 * Settings > Remote buttons: press a button on the remote, then pick the page it opens.
 * The Home override accessibility service catches the button anywhere on the TV,
 * including inside other apps.
 */
@Composable
fun RemoteButtonsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mappings by remember { mutableStateOf(RemoteButtonStore.all(context)) }
    var step by remember { mutableStateOf<RemoteButtonsStep>(RemoteButtonsStep.List) }

    BackHandler {
        if (step == RemoteButtonsStep.List) onBack() else step = RemoteButtonsStep.List
    }

    Column(Modifier.fillMaxSize().background(ScreenBackground).then(ScreenPadding)) {
        when (val current = step) {
            RemoteButtonsStep.List -> {
                ScreenTitle(
                    "REMOTE BUTTONS",
                    "Choose what a button on your remote opens. It works anywhere on the TV, even inside other apps.",
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    item {
                        SettingsRow(
                            title = "Add a button",
                            detail = "Press a button on your remote, then pick where it goes",
                            action = "ADD",
                            onClick = { step = RemoteButtonsStep.Capturing },
                            requestInitialFocus = true,
                        )
                    }
                    if (mappings.isNotEmpty()) item { SettingsSection("Your buttons") }
                    items(mappings.entries.sortedBy { RemoteButtons.label(it.key) }, key = { it.key }) { (keyCode, destination) ->
                        SettingsRow(
                            title = "${RemoteButtons.label(keyCode)} button",
                            detail = "Opens ${destination.label}" +
                                (RemoteButtons.inAppUse(keyCode)?.let { "  •  $it" } ?: ""),
                            action = "CHANGE",
                            onClick = { step = RemoteButtonsStep.Choosing(keyCode) },
                        )
                    }
                }
            }

            RemoteButtonsStep.Capturing -> {
                DisposableEffect(Unit) {
                    RemoteButtonCapture.listener = { keyCode -> step = RemoteButtonsStep.Choosing(keyCode) }
                    onDispose { RemoteButtonCapture.listener = null }
                }
                ScreenTitle("PRESS A BUTTON", "Press the button on your remote you want to use.")
                Spacer(Modifier.height(16.dp))
                Text(
                    "Waiting for a button…",
                    color = ScreenPaper,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "The arrows, OK, Back, Home, volume, power and number buttons can't be changed.",
                    color = ScreenMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                SettingsRow(
                    title = "Cancel",
                    detail = null,
                    action = "BACK",
                    onClick = { step = RemoteButtonsStep.List },
                    requestInitialFocus = true,
                )
            }

            is RemoteButtonsStep.Choosing -> key(current.keyCode) {
                val keyCode = current.keyCode
                val existing = mappings[keyCode]
                ScreenTitle(
                    "${RemoteButtons.label(keyCode).uppercase()} BUTTON",
                    "What should this button open?" + (RemoteButtons.inAppUse(keyCode)?.let { " $it, which this replaces." } ?: ""),
                )
                Spacer(Modifier.height(16.dp))
                fun save(destination: ButtonDestination?) {
                    RemoteButtonStore.set(context, keyCode, destination)
                    mappings = RemoteButtonStore.all(context)
                    step = RemoteButtonsStep.List
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    items(ButtonDestination.entries, key = { it.name }) { destination ->
                        SettingsRow(
                            title = destination.label,
                            detail = if (destination == existing) "Current choice" else null,
                            action = "SELECT",
                            onClick = { save(destination) },
                            selected = destination == existing,
                            requestInitialFocus = destination == (existing ?: ButtonDestination.entries.first()),
                        )
                    }
                    if (existing != null) {
                        item {
                            SettingsRow(
                                title = "Remove",
                                detail = "Give this button back to the TV",
                                action = "REMOVE",
                                onClick = { save(null) },
                            )
                        }
                    }
                }
            }
        }
    }
}
