package io.github.lozza.tellygrid.playback

import android.content.Context
import android.view.KeyEvent

/** Pages a remote button can be set to open. */
enum class ButtonDestination(val label: String) {
    HOME("Home"),
    APPS("Apps"),
    GUIDE("TV Guide"),
    FAVOURITES("Favourites"),
    SETTINGS("Settings"),
}

/**
 * Which remote buttons can be remapped, what to call them, and how mappings are saved.
 * Kept free of Android runtime calls (other than [label]'s fallback) so it can be unit tested.
 */
object RemoteButtons {
    /** Buttons TellyGrid (or the TV) needs to keep working normally. */
    private val RESERVED = setOf(
        KeyEvent.KEYCODE_UNKNOWN,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyEvent.KEYCODE_DPAD_UP_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    )

    private val NAMES = mapOf(
        KeyEvent.KEYCODE_SETTINGS to "Settings",
        KeyEvent.KEYCODE_GUIDE to "Guide",
        KeyEvent.KEYCODE_INFO to "Info",
        KeyEvent.KEYCODE_MENU to "Menu",
        KeyEvent.KEYCODE_PROG_RED to "Red",
        KeyEvent.KEYCODE_PROG_GREEN to "Green",
        KeyEvent.KEYCODE_PROG_YELLOW to "Yellow",
        KeyEvent.KEYCODE_PROG_BLUE to "Blue",
        KeyEvent.KEYCODE_MEDIA_RECORD to "Record",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play/Pause",
        KeyEvent.KEYCODE_MEDIA_PLAY to "Play",
        KeyEvent.KEYCODE_MEDIA_PAUSE to "Pause",
        KeyEvent.KEYCODE_MEDIA_STOP to "Stop",
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "Fast forward",
        KeyEvent.KEYCODE_MEDIA_REWIND to "Rewind",
        KeyEvent.KEYCODE_MEDIA_NEXT to "Next",
        KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Previous",
        KeyEvent.KEYCODE_CHANNEL_UP to "Channel up",
        KeyEvent.KEYCODE_CHANNEL_DOWN to "Channel down",
        KeyEvent.KEYCODE_LAST_CHANNEL to "Last channel",
        KeyEvent.KEYCODE_TV to "TV",
        KeyEvent.KEYCODE_TV_INPUT to "Source",
        KeyEvent.KEYCODE_CAPTIONS to "Subtitles",
        KeyEvent.KEYCODE_TV_TELETEXT to "Text",
        KeyEvent.KEYCODE_BOOKMARK to "Bookmark",
        KeyEvent.KEYCODE_DVR to "Recordings",
        KeyEvent.KEYCODE_ALL_APPS to "Apps",
        KeyEvent.KEYCODE_SEARCH to "Search",
        KeyEvent.KEYCODE_ASSIST to "Assistant",
        KeyEvent.KEYCODE_VOICE_ASSIST to "Voice",
        KeyEvent.KEYCODE_EXPLORER to "Browser",
    )

    /** What a button already does inside TellyGrid, which a mapping would replace. */
    private val IN_APP_USES = mapOf(
        KeyEvent.KEYCODE_MEDIA_RECORD to "Record also adds favourites in TellyGrid",
        KeyEvent.KEYCODE_INFO to "Info also opens programme options in TellyGrid",
        KeyEvent.KEYCODE_MENU to "Menu also opens options in TellyGrid",
    )

    fun canRemap(keyCode: Int): Boolean = keyCode !in RESERVED && !isDigit(keyCode)

    private fun isDigit(keyCode: Int) =
        keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9

    fun label(keyCode: Int): String = NAMES[keyCode] ?: runCatching {
        KeyEvent.keyCodeToString(keyCode)
            .removePrefix("KEYCODE_")
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }.getOrNull()?.takeUnless { it.isBlank() || it.all(Char::isDigit) } ?: "Button $keyCode"

    fun inAppUse(keyCode: Int): String? = IN_APP_USES[keyCode]

    fun encode(mappings: Map<Int, ButtonDestination>): Set<String> =
        mappings.map { (key, destination) -> "$key=${destination.name}" }.toSet()

    fun decode(saved: Set<String>?): Map<Int, ButtonDestination> = saved.orEmpty().mapNotNull { entry ->
        val key = entry.substringBefore('=').toIntOrNull() ?: return@mapNotNull null
        val destination = ButtonDestination.entries.firstOrNull { it.name == entry.substringAfter('=') }
            ?: return@mapNotNull null
        if (canRemap(key)) key to destination else null
    }.toMap()
}

/**
 * Saved button mappings. The accessibility service and Settings run in the same process,
 * so a cached copy is shared and the service doesn't touch storage on every key press.
 */
object RemoteButtonStore {
    private const val PREFS = "tellygrid_remote_buttons"
    private const val KEY = "mappings"

    @Volatile private var cache: Map<Int, ButtonDestination>? = null

    fun all(context: Context): Map<Int, ButtonDestination> = cache ?: synchronized(this) {
        cache ?: RemoteButtons.decode(prefs(context).getStringSet(KEY, emptySet())).also { cache = it }
    }

    fun destination(context: Context, keyCode: Int): ButtonDestination? = all(context)[keyCode]

    fun set(context: Context, keyCode: Int, destination: ButtonDestination?) = synchronized(this) {
        val updated = all(context).toMutableMap().apply {
            if (destination == null) remove(keyCode) else put(keyCode, destination)
        }
        prefs(context).edit().putStringSet(KEY, RemoteButtons.encode(updated)).apply()
        cache = updated
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * While Settings is waiting for "press the button you want to use", the accessibility
 * service hands the next remappable button here instead of acting on it. Catching it in
 * the service means buttons the TV would normally grab itself (like Settings) still work.
 */
object RemoteButtonCapture {
    @Volatile var listener: ((Int) -> Unit)? = null
}
