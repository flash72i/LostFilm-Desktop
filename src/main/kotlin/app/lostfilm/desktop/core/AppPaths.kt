package app.lostfilm.desktop.core

import java.io.File

object AppPaths {
    private val exeDir: File = runCatching {
        val command = ProcessHandle.current().info().command().orElse(null)
        if (command != null) File(command).parentFile else null
    }.getOrNull() ?: File(System.getProperty("user.dir"))

    val portable: Boolean = File(exeDir, "portable.marker").exists()

    val appDir: File get() = exeDir

    val dataDir: File = run {
        val dir = if (portable) {
            File(exeDir, "data")
        } else {
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "LostFilmDesktop")
        }
        dir.mkdirs()
        dir
    }

    val settingsFile: File get() = File(dataDir, "settings.properties")
    val cookiesFile: File get() = File(dataDir, "cookies.tsv")
}
