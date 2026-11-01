package io.github.lozza.tellygrid

import io.github.lozza.tellygrid.data.FavouriteShow
import io.github.lozza.tellygrid.data.FavouriteSource
import io.github.lozza.tellygrid.data.GuideChannel
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.Programme
import io.github.lozza.tellygrid.data.TvPublishedProgramme
import io.github.lozza.tellygrid.data.asFavourite
import io.github.lozza.tellygrid.data.decodeFavouriteShow
import io.github.lozza.tellygrid.data.encodeFavouriteShow
import io.github.lozza.tellygrid.data.favouriteKeyForGuide
import io.github.lozza.tellygrid.data.nextAiring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FavouriteShowsTest {
    private val now = Instant.parse("2026-11-01T20:15:00Z")

    private fun programme(title: String, startOffsetMinutes: Long, lengthMinutes: Long = 60) = Programme(
        id = "$title-$startOffsetMinutes",
        title = title,
        synopsis = "",
        startsAt = now.plus(Duration.ofMinutes(startOffsetMinutes)),
        endsAt = now.plus(Duration.ofMinutes(startOffsetMinutes + lengthMinutes)),
    )

    private fun channel(id: String, vararg programmes: Programme) = GuideChannel(
        id = id,
        number = 1,
        name = id.uppercase(),
        providerLabel = "Freeview",
        accentArgb = 0,
        playback = PlaybackTarget.Unavailable("test"),
        programmes = programmes.toList(),
    )

    @Test
    fun `favourite survives encoding with awkward characters`() {
        val show = FavouriteShow(
            key = "app|com.netflix|strangerthings",
            source = FavouriteSource.APP,
            title = "Stranger Things\tSeason 5 | \"Finale\"",
            subtitle = "Netflix",
            artworkUri = "https://example.com/a.jpg",
            packageName = "com.netflix.ninja",
            intentUri = "intent://title/123#Intent;end",
            addedAtMillis = 42L,
        )
        assertEquals(show, decodeFavouriteShow(encodeFavouriteShow(show)))
        assertNull(decodeFavouriteShow("not a favourite"))
    }

    @Test
    fun `same show from different rows of one app is one favourite`() {
        val watchNext = TvPublishedProgramme("Slow Horses", "com.apple", "intent://a", null)
        val preview = TvPublishedProgramme("SLOW HORSES ", "com.apple", "intent://b", null)
        assertEquals(watchNext.asFavourite("Apple TV").key, preview.asFavourite("Apple TV").key)
    }

    @Test
    fun `guide favourite finds the airing on now before a later one`() {
        val saved = programme("Gogglebox", 0).asFavourite(channel("c4"))
        val channels = listOf(
            channel("e4", programme("Gogglebox", -10)),
            channel("c4", programme("Gogglebox", 120)),
        )
        val airing = saved.nextAiring(channels, now)!!
        assertTrue(airing.onNow)
        assertEquals("e4", airing.channel.id)
    }

    @Test
    fun `guide favourite falls back to the next airing, preferring the saved channel on a tie`() {
        val saved = programme("Gogglebox", 0).asFavourite(channel("c4"))
        val channels = listOf(
            channel("e4", programme("Gogglebox", 60)),
            channel("c4", programme("Gogglebox", 60), programme("News", -15)),
        )
        val airing = saved.nextAiring(channels, now)!!
        assertFalse(airing.onNow)
        assertEquals("c4", airing.channel.id)
        assertEquals(favouriteKeyForGuide("gogglebox"), saved.key)
    }

    @Test
    fun `guide favourite with no future airing has none`() {
        val saved = programme("Gogglebox", 0).asFavourite(channel("c4"))
        assertNull(saved.nextAiring(listOf(channel("c4", programme("Gogglebox", -120))), now))
    }
}
