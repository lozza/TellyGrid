package io.github.lozza.tellygrid

import android.view.KeyEvent
import io.github.lozza.tellygrid.playback.ButtonDestination
import io.github.lozza.tellygrid.playback.RemoteButtons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteButtonsTest {
    @Test
    fun navigationAndSystemButtonsCannotBeRemapped() {
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_9,
        ).forEach { assertFalse(RemoteButtons.canRemap(it)) }
    }

    @Test
    fun remoteExtrasCanBeRemapped() {
        listOf(
            KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_MEDIA_RECORD,
        ).forEach { assertTrue(RemoteButtons.canRemap(it)) }
        assertEquals("Settings", RemoteButtons.label(KeyEvent.KEYCODE_SETTINGS))
    }

    @Test
    fun mappingsSurviveSaving() {
        val mappings = mapOf(
            KeyEvent.KEYCODE_SETTINGS to ButtonDestination.GUIDE,
            KeyEvent.KEYCODE_PROG_RED to ButtonDestination.FAVOURITES,
        )
        assertEquals(mappings, RemoteButtons.decode(RemoteButtons.encode(mappings)))
    }

    @Test
    fun brokenOrReservedEntriesAreDropped() {
        val saved = setOf("176=GUIDE", "nope", "183=NOWHERE", "${KeyEvent.KEYCODE_BACK}=HOME")
        assertEquals(mapOf(176 to ButtonDestination.GUIDE), RemoteButtons.decode(saved))
    }
}
