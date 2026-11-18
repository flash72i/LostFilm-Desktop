package app.lostfilm.desktop.core

import java.io.File
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit

data class SavedCredentials(
    val email: String = "",
    val password: String = "",
)

object CredentialsStore {
    private const val ENCODING_KEY = "password.encoding"
    private const val DPAPI = "dpapi"

    private val file: File get() = File(AppPaths.dataDir, "credentials.properties")
    private val legacyBackup: File get() = File(
        System.getenv("APPDATA") ?: System.getProperty("user.home"),
        "LostFilmDesktop/credentials.properties",
    )

    fun load(): SavedCredentials {
        val primary = read(file)
        if (primary.email.isNotBlank() || primary.password.isNotBlank()) return primary

        val backup = read(legacyBackup)
        if (backup.email.isNotBlank() || backup.password.isNotBlank()) {
            save(backup.email, backup.password)
            if (legacyBackup.absolutePath != file.absolutePath) {
                runCatching { legacyBackup.delete() }
            }
        }
        return backup
    }

    fun save(email: String, password: String) {
        val props = Properties()
        props.setProperty("email", email)
        val encrypted = Dpapi.protect(password.toByteArray(Charsets.UTF_8))
        if (encrypted != null) {
            props.setProperty("password", Base64.getEncoder().encodeToString(encrypted))
            props.setProperty(ENCODING_KEY, DPAPI)
        } else {
            props.setProperty("password", password)
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "LostFilm Desktop credentials") }
        }
        restrict(file)
        if (legacyBackup.absolutePath != file.absolutePath && legacyBackup.exists()) {
            runCatching { legacyBackup.delete() }
        }
    }

    fun clear() {
        if (file.exists()) file.delete()
        if (legacyBackup.absolutePath != file.absolutePath && legacyBackup.exists()) {
            legacyBackup.delete()
        }
    }

    private fun read(source: File): SavedCredentials {
        if (!source.exists()) return SavedCredentials()
        val props = Properties()
        runCatching { source.inputStream().use { props.load(it) } }
        val stored = props.getProperty("password", "")
        val password = if (props.getProperty(ENCODING_KEY, "") == DPAPI) decode(stored) else stored
        return SavedCredentials(
            email = props.getProperty("email", "").trim(),
            password = password,
        )
    }

    private fun decode(value: String): String = runCatching {
        val bytes = Base64.getDecoder().decode(value)
        Dpapi.unprotect(bytes)?.toString(Charsets.UTF_8).orEmpty()
    }.getOrDefault("")

    private fun restrict(target: File) {
        if (!target.isFile) return
        runCatching {
            ProcessBuilder("attrib", "+H", target.absolutePath)
                .start()
                .waitFor(3, TimeUnit.SECONDS)
        }
        val user = System.getProperty("user.name").orEmpty()
        if (user.isBlank()) return
        runCatching {
            ProcessBuilder(
                "icacls",
                target.absolutePath,
                "/inheritance:r",
                "/grant:r",
                "$user:F",
            ).start().waitFor(5, TimeUnit.SECONDS)
        }
    }
}
