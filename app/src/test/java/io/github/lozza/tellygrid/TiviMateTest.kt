package io.github.lozza.tellygrid

import android.media.tv.TvContract
import io.github.lozza.tellygrid.data.GuideFilter
import io.github.lozza.tellygrid.data.GuideSettings
import io.github.lozza.tellygrid.data.PlaybackTarget
import io.github.lozza.tellygrid.data.TiviMate
import io.github.lozza.tellygrid.data.TvPublishedProgramme
import io.github.lozza.tellygrid.data.TvPublishedRecommendations
import io.github.lozza.tellygrid.data.forGuideSettings
import io.github.lozza.tellygrid.data.withLocalWatchNext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TiviMateTest {
    private val now = Instant.parse("2026-11-02T18:00:00Z")

    private fun item(title: String, pkg: String = "ar.tvplayer.tv", type: Int? = TvContract.PreviewPrograms.TYPE_CHANNEL, at: Long? = null) =
        TvPublishedProgramme(
            title = title,
            packageName = pkg,
            intentUri = "intent:#Intent;package=$pkg;S.channel=$title;end",
            artworkUri = null,
            lastEngagedAtMillis = at,
            type = type,
        )

    @Test
    fun guideListsOnlyTiviMateChannels() {
        val published = TvPublishedRecommendations(
            preview = listOf(
                item("Sky News"),
                item("A Film", type = TvContract.PreviewPrograms.TYPE_MOVIE),
                item("Netflix Show", pkg = "com.netflix.ninja"),
            ),
            watchNext = listOf(item("sky news"), item("BBC One")),
            loaded = true,
        )
        val channels = TiviMate.guideChannels(published, now)
        assertEquals(listOf("BBC One", "Sky News"), channels.map { it.name })
        assertTrue(channels.all { it.playback is PlaybackTarget.PublishedChannel })
        assertTrue(channels.all { ch -> ch.programmes.single().let { it.startsAt < now && it.endsAt > now } })
    }

    @Test
    fun tiviMateFilterShowsOnlyTiviMateChannels() {
        val channels = TiviMate.guideChannels(TvPublishedRecommendations(preview = listOf(item("Sky News"))), now)
        assertEquals(1, channels.forGuideSettings(GuideSettings(filter = GuideFilter.TIVIMATE)).size)
    }

    @Test
    fun localWatchNextGoesFirstWithoutDuplicates() {
        val published = TvPublishedRecommendations(watchNext = listOf(item("Old", pkg = "com.other", at = 100), item("Sky News", at = 50)))
        val merged = published.withLocalWatchNext(listOf(item("Sky News", at = 500)))
        assertEquals(listOf("Sky News", "Old"), merged.watchNext.map { it.title })
        assertEquals(500L, merged.watchNext.first().lastEngagedAtMillis)
    }
}
