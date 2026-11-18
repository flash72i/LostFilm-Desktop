package app.lostfilm.desktop.core

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.filechooser.FileSystemView

data class PlayerEntry(
    val name: String,
    val path: String,
)

object PlayerRegistry {
    fun defaults(): List<PlayerEntry> {
        val found = LinkedHashMap<String, PlayerEntry>()

        fun add(name: String, path: String) {
            val file = File(path)
            if (!file.isFile) return
            found.putIfAbsent(file.absolutePath.lowercase(), PlayerEntry(name, file.absolutePath))
        }

        listOf(
            File(AppPaths.appDir, "players"),
            File(AppPaths.appDir, "player"),
            File(AppPaths.dataDir, "players"),
        ).forEach { dir ->
            dir.listFiles()
                ?.filter { it.isFile && it.extension.equals("exe", true) && isPlayerExe(it.name) }
                ?.sortedBy { it.name.lowercase() }
                ?.forEach { add(it.nameWithoutExtension, it.absolutePath) }
        }

        add("VLC", "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe")
        add("VLC (x86)", "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe")

        add("PotPlayer", "C:\\Program Files\\DAUM\\PotPlayer\\PotPlayerMini64.exe")
        add("PotPlayer", "C:\\Program Files\\PotPlayer\\PotPlayerMini64.exe")
        add("GOM Player", "C:\\Program Files\\GRETECH\\GOMPlayerPlus\\GOM64.EXE")
        add("KMPlayer", "C:\\Program Files (x86)\\The KMPlayer\\KMPlayer.exe")

        return found.values.toList()
    }

    fun sanitize(players: List<PlayerEntry>): List<PlayerEntry> =
        players
            .filter { it.path.isNotBlank() && File(it.path).isFile }
            .distinctBy { it.path.lowercase() }

    fun isVlc(player: PlayerEntry): Boolean =
        player.path.endsWith("vlc.exe", true) || player.name.contains("vlc", true)

    fun iconFile(player: PlayerEntry): File? {
        val exe = File(player.path)
        if (!exe.isFile) return null
        val dir = File(AppPaths.dataDir, "player-icons").apply { mkdirs() }
        val target = File(
            dir,
            Integer.toHexString(player.path.hashCode()) + "-" + exe.lastModified() + ".png",
        )
        if (target.isFile) return target

        val icon = runCatching {
            FileSystemView.getFileSystemView().getSystemIcon(exe, 48, 48)
        }.getOrNull() ?: return null
        val image = BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        icon.paintIcon(null, graphics, 0, 0)
        graphics.dispose()
        ImageIO.write(image, "png", target)
        return target
    }

    private fun isPlayerExe(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "vlc.exe" ||
            lower.startsWith("potplayer") ||
            lower == "mpv.exe" ||
            lower.startsWith("mpc-hc") ||
            lower == "kmplayer.exe" ||
            lower == "gom64.exe" ||
            lower == "gomplayer.exe"
    }
}
