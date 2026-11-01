package io.github.lozza.tellygrid

import io.github.lozza.tellygrid.update.compareVersions
import io.github.lozza.tellygrid.update.isNewerVersion
import io.github.lozza.tellygrid.update.parseNewestApkRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateReleaseTest {
    @Test
    fun `versions compare as semantic versions with numeric pre-release parts`() {
        assertTrue(isNewerVersion("0.8.0-alpha.8", "0.7.0-alpha.7"))
        assertTrue(isNewerVersion("0.7.0-alpha.10", "0.7.0-alpha.9"))
        assertTrue(isNewerVersion("0.7.0", "0.7.0-alpha.7"))
        assertTrue(isNewerVersion("v1.0.0", "0.9.9"))
        assertFalse(isNewerVersion("0.7.0-alpha.7", "0.7.0-alpha.7"))
        assertFalse(isNewerVersion("0.6.0-alpha.6", "0.7.0-alpha.7"))
        assertFalse(isNewerVersion("0.7.0-alpha.7", "0.7.0"))
        assertFalse(isNewerVersion("nightly", "0.7.0"))
        assertEquals(0, compareVersions("0.7.0", "v0.7"))
    }

    @Test
    fun `newest release with an apk wins whether or not it is a pre-release`() {
        val json = """
            [
              {"tag_name": "v0.9.0-alpha.9", "draft": true, "prerelease": true,
               "assets": [{"name": "TellyGrid.apk", "browser_download_url": "https://example.com/draft.apk", "size": 1}]},
              {"tag_name": "v0.8.1", "draft": false, "prerelease": false, "assets": []},
              {"tag_name": "v0.8.0-alpha.8", "name": "TellyGrid v0.8.0-alpha.8", "draft": false, "prerelease": true,
               "body": "Notes", "html_url": "https://github.com/lozza/TellyGrid/releases/tag/v0.8.0-alpha.8",
               "assets": [
                 {"name": "checksums.txt", "browser_download_url": "https://example.com/checksums.txt", "size": 10},
                 {"name": "TellyGrid-0.8.0-alpha.8.apk", "browser_download_url": "https://example.com/a8.apk", "size": 15000000}
               ]},
              {"tag_name": "v0.7.0-alpha.7", "draft": false, "prerelease": false,
               "assets": [{"name": "TellyGrid-0.7.0-alpha.7.apk", "browser_download_url": "https://example.com/a7.apk", "size": 14000000}]}
            ]
        """.trimIndent()

        val release = parseNewestApkRelease(json)!!
        assertEquals("0.8.0-alpha.8", release.version)
        assertEquals("https://example.com/a8.apk", release.apkUrl)
        assertEquals(15000000L, release.apkSizeBytes)
        assertTrue(release.prerelease)
        assertEquals("TellyGrid v0.8.0-alpha.8", release.title)
    }

    @Test
    fun `no usable release gives null`() {
        assertNull(parseNewestApkRelease("[]"))
        assertNull(parseNewestApkRelease("""[{"tag_name": "v1.0.0", "assets": [{"name": "app.apk", "browser_download_url": "http://insecure/app.apk"}]}]"""))
    }
}
