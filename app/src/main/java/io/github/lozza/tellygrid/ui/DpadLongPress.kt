package io.github.lozza.tellygrid.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Tells a press of the remote's OK button from a hold. Compose's own long-click doesn't
 * fire for D-pad OK on Android TV remotes: the press opens the item on release. While OK
 * is held, the remote repeats its key-down, so the first repeat (after about half a
 * second) counts as a hold and the release that follows is ignored.
 */
fun Modifier.dpadOkPress(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    if (onLongClick == null) this else composed {
        var downSeen by remember { mutableStateOf(false) }
        var longFired by remember { mutableStateOf(false) }
        onPreviewKeyEvent { event ->
            if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                return@onPreviewKeyEvent false
            }
            val native = event.nativeKeyEvent
            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (native.repeatCount == 0) {
                        downSeen = true
                        longFired = false
                    } else if (downSeen && !longFired) {
                        longFired = true
                        onLongClick()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    // A release without our own key-down belongs to a press that started
                    // elsewhere (e.g. the OK that opened this screen), so ignore it.
                    if (downSeen && !longFired) onClick()
                    downSeen = false
                    longFired = false
                    true
                }
                else -> false
            }
        }
    }
