package app.lostfilm.desktop

import app.lostfilm.desktop.data.PlayLink
import app.lostfilm.desktop.data.PlaybackSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSourcesTest {
    private val links = listOf(
        PlayLink("https://cdn/a_480p.mp4", "480p"),
        PlayLink("https://cdn/a_720p.mp4", "720p"),
        PlayLink("https://cdn/a_1080p.mp4", "1080p"),
    )

    @Test
    fun picksRequestedQuality() {
        assertEquals("https://cdn/a_720p.mp4", PlaybackSources.pickDirect(links, "720p")?.url)
        assertEquals("https://cdn/a_1080p.mp4", PlaybackSources.pickDirect(links, "1080p")?.url)
    }

    @Test
    fun fallsBackToFirstLink() {
        assertEquals("https://cdn/a_480p.mp4", PlaybackSources.pickDirect(links, "SD")?.url)
    }

    @Test
    fun emptyListGivesNull() {
        assertNull(PlaybackSources.pickDirect(emptyList(), "720p"))
    }
}
