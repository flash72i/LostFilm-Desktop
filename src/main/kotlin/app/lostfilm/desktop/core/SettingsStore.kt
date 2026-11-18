package app.lostfilm.desktop.core

import java.util.Properties

private val DEFAULT_MIRRORS = listOf(
    "www.lostfilm.download",
    "lostfilm.run",
    "lostfilmtv5.site",
    "www.lostfilm.tv",
)

val HOME_ROW_ORDER = listOf("episodes", "favorites", "series", "newSeries", "history", "movies")

val HOME_ROW_TITLES = mapOf(
    "episodes" to "Новые эпизоды",
    "favorites" to "Избранные сериалы",
    "series" to "Все сериалы",
    "newSeries" to "Новые сериалы",
    "history" to "История просмотра",
    "movies" to "Фильмы",
)

val DEFAULT_HOME_ROWS = HOME_ROW_ORDER

data class AppSettings(
    val host: String = "www.lostfilm.download",
    val imageHost: String = "",
    val hostType: String = "orig",
    val autoQuality: String = "NONE",
    val autoPlayLow: Boolean = false,
    val fullSeason: Boolean = false,
    val cardScale: Float = 0.85f,
    val hideWatched: Boolean = false,
    val onlyFavoriteNew: Boolean = false,
    val sortFav: String = "title",
    val sortAll: String = "title",
    val hideFinished: Boolean = false,
    val hideNotStarted: Boolean = false,
    val skipDetails: Boolean = false,
    val autoWatched: Boolean = false,
    val showWatchDialog: Boolean = true,
    val channelOnlyFavorite: Boolean = false,
    val channelOnlyUnwatched: Boolean = false,
    val chserieOnlyWidePoster: Boolean = true,
    val chserieHideNotStarted: Boolean = false,
    val logged: Boolean = false,
    val userName: String = "",
    val players: List<PlayerEntry> = emptyList(),
    val defaultPlayer: String = "",
    val askPlayer: Boolean = true,
    val autoMirror: Boolean = true,
    val mirrors: List<String> = DEFAULT_MIRRORS,
    val homeRows: List<String> = DEFAULT_HOME_ROWS,
)

object SettingsStore {
    fun load(): AppSettings {
        val defaults = PlayerRegistry.defaults()
        if (!AppPaths.settingsFile.exists()) {
            return applyPlayers(AppSettings(), defaults)
        }

        val props = Properties()
        runCatching { loadInto(props) }

        val count = props.getProperty("players.count", "0").toIntOrNull() ?: 0
        val storedPlayers = (0 until count).mapNotNull { index ->
            val path = props.getProperty("player.$index.path").orEmpty()
            if (path.isBlank()) return@mapNotNull null
            val name = props.getProperty("player.$index.name").orEmpty()
                .ifBlank { java.io.File(path).nameWithoutExtension }
            PlayerEntry(name, path)
        }

        val settings = AppSettings(
            host = props.getProperty("host", "www.lostfilm.download"),
            imageHost = props.getProperty("imageHost", ""),
            hostType = props.getProperty("hostType", "orig"),
            autoQuality = props.getProperty("autoQuality", "NONE"),
            autoPlayLow = props.getProperty("autoPlayLow", "false").toBoolean(),
            fullSeason = props.getProperty("fullSeason", "false").toBoolean(),
            cardScale = props.getProperty("cardScale", "0.85").toFloatOrNull() ?: 0.85f,
            hideWatched = props.getProperty("hideWatched", "false").toBoolean(),
            onlyFavoriteNew = props.getProperty("onlyFavoriteNew", "false").toBoolean(),
            sortFav = props.getProperty("sortFav", "title"),
            sortAll = props.getProperty("sortAll", "title"),
            hideFinished = props.getProperty("hideFinished", "false").toBoolean(),
            hideNotStarted = props.getProperty("hideNotStarted", "false").toBoolean(),
            skipDetails = props.getProperty("skipDetails", "false").toBoolean(),
            autoWatched = props.getProperty("autoWatched", "false").toBoolean(),
            showWatchDialog = props.getProperty("showWatchDialog", "true").toBoolean(),
            channelOnlyFavorite = props.getProperty("channelOnlyFavorite", "false").toBoolean(),
            channelOnlyUnwatched = props.getProperty("channelOnlyUnwatched", "false").toBoolean(),
            chserieOnlyWidePoster = props.getProperty("chserieOnlyWidePoster", "true").toBoolean(),
            chserieHideNotStarted = props.getProperty("chserieHideNotStarted", "false").toBoolean(),
            logged = props.getProperty("logged", "false").toBoolean(),
            userName = props.getProperty("userName", ""),
            players = storedPlayers,
            defaultPlayer = props.getProperty("defaultPlayer", ""),
            askPlayer = props.getProperty("askPlayer", "true").toBoolean(),
            autoMirror = props.getProperty("autoMirror", "true").toBoolean(),
            mirrors = run {
                val count = props.getProperty("mirrors.count", "0").toIntOrNull() ?: 0
                (0 until count).mapNotNull { index ->
                    props.getProperty("mirror.$index").orEmpty().takeIf { it.isNotBlank() }
                }.ifEmpty { DEFAULT_MIRRORS }.let { (it + DEFAULT_MIRRORS).distinct() }
            },
            homeRows = props.getProperty("homeRows")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it in HOME_ROW_ORDER }
                ?.distinct()
                ?: DEFAULT_HOME_ROWS,
        )

        return applyPlayers(settings, storedPlayers + defaults)
    }

    fun save(settings: AppSettings) {
        val props = Properties()
        props.setProperty("host", settings.host)
        props.setProperty("imageHost", settings.imageHost)
        props.setProperty("hostType", settings.hostType)
        props.setProperty("autoQuality", settings.autoQuality)
        props.setProperty("autoPlayLow", settings.autoPlayLow.toString())
        props.setProperty("fullSeason", settings.fullSeason.toString())
        props.setProperty("cardScale", settings.cardScale.toString())
        props.setProperty("hideWatched", settings.hideWatched.toString())
        props.setProperty("onlyFavoriteNew", settings.onlyFavoriteNew.toString())
        props.setProperty("sortFav", settings.sortFav)
        props.setProperty("sortAll", settings.sortAll)
        props.setProperty("hideFinished", settings.hideFinished.toString())
        props.setProperty("hideNotStarted", settings.hideNotStarted.toString())
        props.setProperty("skipDetails", settings.skipDetails.toString())
        props.setProperty("autoWatched", settings.autoWatched.toString())
        props.setProperty("showWatchDialog", settings.showWatchDialog.toString())
        props.setProperty("channelOnlyFavorite", settings.channelOnlyFavorite.toString())
        props.setProperty("channelOnlyUnwatched", settings.channelOnlyUnwatched.toString())
        props.setProperty("chserieOnlyWidePoster", settings.chserieOnlyWidePoster.toString())
        props.setProperty("chserieHideNotStarted", settings.chserieHideNotStarted.toString())
        props.setProperty("logged", settings.logged.toString())
        props.setProperty("userName", settings.userName)

        val players = settings.players.distinctBy { it.path.lowercase() }
        props.setProperty("players.count", players.size.toString())
        players.forEachIndexed { index, player ->
            props.setProperty("player.$index.name", player.name)
            props.setProperty("player.$index.path", player.path)
        }
        props.setProperty("defaultPlayer", settings.defaultPlayer)
        props.setProperty("askPlayer", settings.askPlayer.toString())
        props.setProperty("autoMirror", settings.autoMirror.toString())
        props.setProperty("homeRows", settings.homeRows.joinToString(","))
        val mirrors = settings.mirrors.distinct()
        props.setProperty("mirrors.count", mirrors.size.toString())
        mirrors.forEachIndexed { index, mirror -> props.setProperty("mirror.$index", mirror) }

        AppPaths.settingsFile.outputStream().use { props.store(it, "LostFilm Desktop settings") }
    }

    private fun loadInto(target: Properties) {
        if (!AppPaths.settingsFile.exists()) return
        val bytes = AppPaths.settingsFile.readBytes()
        val utf8 = bytes.toString(Charsets.UTF_8)
        val text = if (utf8.contains('\uFFFD')) bytes.toString(Charsets.ISO_8859_1) else utf8
        text.reader().use { target.load(it) }
    }

    private fun applyPlayers(
        settings: AppSettings,
        players: List<PlayerEntry>,
    ): AppSettings {
        val unique = PlayerRegistry.sanitize(players)
        val selected = settings.defaultPlayer.takeIf { path ->
            unique.any { it.path.equals(path, true) }
        } ?: unique.firstOrNull { PlayerRegistry.isVlc(it) }?.path
            ?: unique.firstOrNull()?.path
            .orEmpty()
        return settings.copy(players = unique, defaultPlayer = selected)
    }
}
