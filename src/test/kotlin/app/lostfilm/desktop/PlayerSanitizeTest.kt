package app.lostfilm.desktop

import app.lostfilm.desktop.core.PlayerEntry
import app.lostfilm.desktop.core.PlayerRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSanitizeTest {
    @Test
    fun dropsMissingFilesAndDuplicates() {
        val existing = File.createTempFile("player", ".exe").apply { deleteOnExit() }
        val players = listOf(
            PlayerEntry("VLC (в комплекте)", "C:/no/such/vlc.exe"),
            PlayerEntry("Real", existing.absolutePath),
            PlayerEntry("Real duplicate", existing.absolutePath.uppercase()),
        )
        val sanitized = PlayerRegistry.sanitize(players)
        assertEquals(1, sanitized.size)
        assertEquals(existing.absolutePath, sanitized.single().path)
    }

    @Test
    fun blankPathIsDropped() {
        val sanitized = PlayerRegistry.sanitize(listOf(PlayerEntry("Empty", "")))
        assertEquals(0, sanitized.size)
    }
}
