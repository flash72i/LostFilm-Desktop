package app.lostfilm.desktop

import app.lostfilm.desktop.data.VlcDownloader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VlcDownloaderTest {
    @Test
    fun picksNewestVersionFromListing() {
        val html = """
            <a href="vlc-3.0.20-win64.zip">vlc-3.0.20-win64.zip</a>
            <a href="vlc-3.0.21-win64.zip">vlc-3.0.21-win64.zip</a>
            <a href="vlc-4.0.0-win64.zip">vlc-4.0.0-win64.zip</a>
            <a href="vlc-3.0.21-win64.7z">vlc-3.0.21-win64.7z</a>
        """.trimIndent()
        assertEquals("vlc-4.0.0-win64.zip", VlcDownloader.pickZipName(html))
    }

    @Test
    fun returnsNullWhenNoArchive() {
        assertNull(VlcDownloader.pickZipName("<html>no builds here</html>"))
    }
}
