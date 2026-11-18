package app.lostfilm.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lostfilm.desktop.core.AppPaths
import app.lostfilm.desktop.core.CredentialsStore
import app.lostfilm.desktop.core.PlayerEntry
import app.lostfilm.desktop.core.PlayerRegistry
import app.lostfilm.desktop.core.SavedCredentials
import app.lostfilm.desktop.core.SettingsStore
import app.lostfilm.desktop.data.CalendarEntry
import app.lostfilm.desktop.data.EpisodeInfo
import app.lostfilm.desktop.data.EpisodeItem
import app.lostfilm.desktop.data.LostFilmApi
import app.lostfilm.desktop.data.PlaybackSources
import app.lostfilm.desktop.data.QualityOption
import app.lostfilm.desktop.data.SeriesDetails
import app.lostfilm.desktop.data.SeriesItem
import app.lostfilm.desktop.data.TorrServerManager
import app.lostfilm.desktop.data.VlcDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val NEW_EPISODES_PAGE = 15
private const val DEFAULT_HOST = "www.lostfilm.download"
private val HOST_PATTERN = Regex("^[a-z0-9.-]+\\.[a-z]{2,}(:\\d+)?$")

sealed interface PendingPlayback {
    data class Episode(val item: EpisodeItem, val quality: QualityOption) : PendingPlayback
    data class Movie(val item: SeriesItem, val quality: QualityOption) : PendingPlayback
    data class Url(val url: String, val title: String) : PendingPlayback
}

sealed interface Screen {
    data object Home : Screen
    data class Details(val item: SeriesItem) : Screen
    data class EpisodeDetails(val item: EpisodeItem) : Screen
    data object Search : Screen
    data object Calendar : Screen
    data object Filters : Screen
    data object Settings : Screen
    data object Login : Screen
    data object About : Screen
}

internal fun List<SeriesItem>.sortedForHome(mode: String): List<SeriesItem> = when (mode) {
    "rating" -> sortedWith(compareByDescending<SeriesItem> { it.rating }.thenByDescending { it.lastUsed })
    "used" -> sortedByDescending { it.lastUsed }
    else -> sortedBy { it.title.lowercase() }
}

class AppModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var settings by mutableStateOf(SettingsStore.load())
        private set

    var mirrorStatus by mutableStateOf<Map<String, Boolean?>>(emptyMap())

    var checkingMirrors by mutableStateOf<Set<String>>(emptySet())
        private set

    var avatarPath by mutableStateOf<String?>(null)
        private set

    val api = LostFilmApi { settings }

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var homeFocusTick by mutableStateOf(0)
        private set

    private val backStack = ArrayDeque<Screen>()

    var homeScrollIndex by mutableStateOf(0)
        private set

    private var pendingHomeReset = true

    fun saveHomeScroll(index: Int) {
        homeScrollIndex = index
    }

    fun consumeHomeReset(): Boolean {
        val value = pendingHomeReset
        pendingHomeReset = false
        return value
    }

    fun canGoBack(): Boolean = backStack.isNotEmpty()

    var series by mutableStateOf<List<SeriesItem>>(emptyList())
        private set

    var movies by mutableStateOf<List<SeriesItem>>(emptyList())
        private set

    var newEpisodes by mutableStateOf<List<EpisodeItem>>(emptyList())
        private set

    var newEpisodesPage by mutableStateOf(1)
        private set

    var newEpisodesFinished by mutableStateOf(false)
        private set

    var newEpisodesLoadingMore by mutableStateOf(false)
        private set

    var history by mutableStateOf<List<EpisodeItem>>(emptyList())
        private set

    var backgroundUrl by mutableStateOf<String?>(null)
        private set

    var qualities by mutableStateOf<List<QualityOption>>(emptyList())
        private set

    var qualitiesLoading by mutableStateOf(false)
        private set

    var playbackBusy by mutableStateOf(false)
        private set

    var pendingPlayback by mutableStateOf<PendingPlayback?>(null)
        private set

    var pendingPlayerTitle by mutableStateOf("")
        private set

    var vlcBusy by mutableStateOf(false)
        private set

    var vlcNote by mutableStateOf<String?>(null)
        private set

    var qualitiesError by mutableStateOf<String?>(null)
        private set

    var movieQualities by mutableStateOf<List<QualityOption>>(emptyList())
        private set

    var movieQualitiesLoading by mutableStateOf(false)
        private set

    var movieQualitiesError by mutableStateOf<String?>(null)
        private set

    private var movieQualitiesFor: String? = null

    var episodeInfo by mutableStateOf<EpisodeInfo?>(null)
        private set

    var episodeInfoLoading by mutableStateOf(false)
        private set

    var episodeInfoError by mutableStateOf<String?>(null)
        private set

    var calendarEntries by mutableStateOf<List<CalendarEntry>>(emptyList())
        private set

    var calendarLoading by mutableStateOf(false)
        private set

    var calendarError by mutableStateOf<String?>(null)
        private set

    var seriesDetails by mutableStateOf<SeriesDetails?>(null)
        private set

    var seriesDetailsLoading by mutableStateOf(false)
        private set

    var seriesDetailsError by mutableStateOf<String?>(null)
        private set

    private var episodeInfoKey: String? = null
    private var seriesDetailsKey: String? = null
    private var searchJob: Job? = null

    var watchedOverrides by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    var favoriteLinks by mutableStateOf<Set<String>>(emptySet())
        private set

    val favorites: List<SeriesItem>
        get() = if (!settings.logged) {
            emptyList()
        } else {
            series.filter { item ->
                if (favoriteLinks.isNotEmpty()) {
                    item.link.trim().trimEnd('/') in favoriteLinks
                } else {
                    !item.notFavorited
                }
            }
                .let { if (settings.hideFinished) it.filter { s -> s.status != "5" } else it }
                .sortedForHome(settings.sortFav)
        }

    val allSeries: List<SeriesItem>
        get() = series.sortedForHome(settings.sortAll)

    val newSeries: List<SeriesItem>
        get() = series
            .let { if (settings.hideNotStarted || settings.chserieHideNotStarted) it.filter { s -> s.statusAuto != 5 } else it }
            .sortedWith(compareByDescending<SeriesItem> { it.date.toIntOrNull() ?: 0 }.thenByDescending { it.id.toIntOrNull() ?: 0 })

    val moviesRow: List<SeriesItem>
        get() = movies.sortedWith(compareByDescending<SeriesItem> { it.date.toIntOrNull() ?: 0 }.thenByDescending { it.id.toIntOrNull() ?: 0 })

    val newEpisodesRow: List<EpisodeItem>
        get() = newEpisodes
            .let { if (settings.hideWatched || settings.channelOnlyUnwatched) it.filterNot { e -> isWatched(e) } else it }
            .let { if (settings.onlyFavoriteNew || settings.channelOnlyFavorite) it.filter { e -> series.firstOrNull { s -> s.id == e.seriesId }?.notFavorited == false } else it }

    var loading by mutableStateOf(false)
        private set

    var homeLoading by mutableStateOf(true)
        private set

    private var catalogLoaded = false
    private var episodesLoaded = false
    private var catalogBusy = false

    private fun markInitialLoaded(catalog: Boolean = false, episodes: Boolean = false) {
        if (catalog) catalogLoaded = true
        if (episodes) episodesLoaded = true
        if (catalogLoaded && episodesLoaded) homeLoading = false
    }

    var message by mutableStateOf<String?>(null)
        private set

    var captchaBytes by mutableStateOf<ByteArray?>(null)
        private set

    var loginBusy by mutableStateOf(false)
        private set

    var loginError by mutableStateOf<String?>(null)
        private set

    var savedCredentials by mutableStateOf(CredentialsStore.load())
        private set

    var searchResults by mutableStateOf<List<SeriesItem>>(emptyList())
        private set

    var filterResults by mutableStateOf<List<SeriesItem>>(emptyList())
        private set

    var filterLoading by mutableStateOf(false)
        private set

    var filterError by mutableStateOf<String?>(null)
        private set

    var lastEpisodeId by mutableStateOf<String?>(null)
        private set

    var searchLoading by mutableStateOf(false)
        private set

    var searchError by mutableStateOf<String?>(null)
        private set

    private val searchHistoryFile = File(AppPaths.dataDir, "search-history.txt")

    var searchHistory by mutableStateOf(readSearchHistory())
        private set

    private fun readSearchHistory(): List<String> = runCatching {
        if (!searchHistoryFile.exists()) {
            emptyList()
        } else {
            searchHistoryFile.readLines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(20)
        }
    }.getOrDefault(emptyList())

    private fun writeSearchHistory() {
        runCatching {
            searchHistoryFile.parentFile?.mkdirs()
            searchHistoryFile.writeText(searchHistory.joinToString("\n"))
        }
    }

    fun rememberSearchQuery(query: String) {
        val value = query.trim()
        if (value.isBlank()) return
        searchHistory = (listOf(value) + searchHistory.filterNot { it.equals(value, true) }).take(20)
        writeSearchHistory()
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
        runCatching { searchHistoryFile.delete() }
    }

    var watchBusy by mutableStateOf(false)
        private set

    var pendingWatchedDialog by mutableStateOf<EpisodeItem?>(null)
        private set

    var followBusy by mutableStateOf(false)
        private set

    fun navigate(target: Screen) {
        if (target == Screen.Home) {
            backStack.clear()
            homeScrollIndex = 0
            pendingHomeReset = true
            homeFocusTick++
        } else if (screen != target) {
            backStack.addLast(screen)
        }
        screen = target
    }

    fun goBack() {
        val previous = backStack.removeLastOrNull()
        if (previous == null || previous == Screen.Home) {
            screen = Screen.Home
        } else {
            screen = previous
        }
    }

    fun goHome() {
        navigate(Screen.Home)
    }

    fun refreshFromHome() {
        navigate(Screen.Home)
        refreshCatalog(background = true)
        refreshNewEpisodes()
    }

    fun applyFilter(
        genres: List<Int>,
        years: List<Int>,
        channels: List<Int>,
        groups: List<Int>,
    ) {
        if (genres.isEmpty() && years.isEmpty() && channels.isEmpty() && groups.isEmpty()) {
            filterResults = emptyList()
            filterError = null
            filterLoading = false
            return
        }
        filterLoading = true
        filterError = null
        scope.launch {
            val found = LinkedHashMap<String, SeriesItem>()
            var offset = 0
            var more = true
            while (more && offset < 200) {
                val page = withContext(Dispatchers.IO) {
                    runCatching { api.filterSerialList(offset, genres, years, channels, groups) }
                }
                page.onSuccess { items ->
                    items.forEach { found[it.id] = it }
                    more = items.size >= 10
                    offset += 10
                }.onFailure {
                    more = false
                    filterError = "Фильтр: ${it.message}"
                }
                if (page.isFailure) break
            }
            filterResults = found.values.toList()
            if (filterError == null && filterResults.isEmpty()) {
                filterError = "Ничего не найдено"
            }
            filterLoading = false
        }
    }

    fun open(item: SeriesItem) {
        val stamped = item.copy(lastUsed = System.currentTimeMillis())
        series = series.map { if (it.id == stamped.id) stamped else it }
        movies = movies.map { if (it.id == stamped.id) stamped else it }
        navigate(Screen.Details(stamped))
    }

    fun baseUrl(): String = api.baseUrl()

    fun imageUrl(path: String): String {
        if (path.isBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val imageBase = settings.imageHost.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (imageBase.isNotBlank() && path.startsWith("/Static/")) {
            val tail = path.removePrefix("/Static/")
            return "https://$imageBase/$tail"
        }
        val base = baseUrl()
        return if (path.startsWith("/")) "$base$path" else "$base/$path"
    }

    fun seriesPoster(item: SeriesItem): String =
        "${baseUrl()}/Static/Images/${item.id}/Posters/poster.jpg"

    fun seriesWide(item: SeriesItem): String =
        "${baseUrl()}/Static/Images/${item.id}/Posters/image.jpg"

    fun dataDir(): File = AppPaths.dataDir

    fun bootstrap() {
        refreshCatalog()
        refreshNewEpisodes()
        refreshAvatar()
    }

    fun refreshAvatar() {
        if (!settings.logged) {
            avatarPath = null
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.avatarUrl() }
            }
            result.onSuccess { avatarPath = it }
        }
    }

    private fun hostHasSession(): Boolean {
        val domains = app.lostfilm.desktop.data.net.Http.cookieJar.sessionDomains()
        val host = settings.host
        return domains.any { domain ->
            host == domain || host.endsWith(".$domain") || domain.endsWith(".$host")
        }
    }

    private fun ensureSession(): Boolean {
        if (settings.host.isBlank()) return false
        return runCatching { api.hasValidSession() }.getOrDefault(false)
    }
    fun refreshCatalog(background: Boolean = false) {
        if (screen == Screen.Login) return
        if (catalogBusy) return
        catalogBusy = true
        if (!background) loading = true
        scope.launch {
            try {
            val sessionOk = withContext(Dispatchers.IO) { ensureSession() }

            if (!sessionOk) {

                message = "Сессия не действует на этом зеркале — войдите заново"

                navigate(Screen.Login)
                if (!background) loading = false
                markInitialLoaded(catalog = true)
                return@launch
            }
            val candidates = if (settings.autoMirror) {
                listOf(settings.host) + settings.mirrors.filterNot { it == settings.host }
            } else {
                listOf(settings.host)
            }
            var lastError: Throwable? = null
            for (candidate in candidates) {
                if (candidate != settings.host) {
                    settings = settings.copy(host = candidate)
                    SettingsStore.save(settings)
                }
                val preview = withContext(Dispatchers.IO) {
                    runCatching {
                        coroutineScope {
                            val seriesPage = async {
                                runCatching { api.loadCatalogPage(SeriesItem.Kind.SERIES, 0) }.getOrDefault(emptyList())
                            }
                            val moviePage = async {
                                runCatching { api.loadCatalogPage(SeriesItem.Kind.MOVIE, 0) }.getOrDefault(emptyList())
                            }
                            seriesPage.await() to moviePage.await()
                        }
                    }
                }
                val firstPages = preview.getOrNull()
                if (firstPages != null &&
                    (firstPages.first.isNotEmpty() || firstPages.second.isNotEmpty())
                ) {
                    if (!background || (series.isEmpty() && movies.isEmpty())) {
                        series = firstPages.first
                        movies = firstPages.second
                    }
                    runCatching { api.favoriteLinks() }.onSuccess { links -> favoriteLinks = links }
                    if (candidate != candidates.first()) {
                        message = "Зеркало переключено: $candidate"
                    }
                    if (!background) loading = false
                    markInitialLoaded(catalog = true)

                    val full = withContext(Dispatchers.IO) {
                        runCatching {
                            coroutineScope {
                                val seriesAll = async { api.loadCatalog(SeriesItem.Kind.SERIES) }
                                val moviesAll = async { api.loadCatalog(SeriesItem.Kind.MOVIE) }
                                seriesAll.await() to moviesAll.await()
                            }
                        }
                    }
                    full.onSuccess { (loadedSeries, loadedMovies) ->
                        series = loadedSeries
                        movies = loadedMovies
                        runCatching { api.favoriteLinks() }.onSuccess { links -> favoriteLinks = links }
                    }
                    return@launch
                }
                lastError = preview.exceptionOrNull()
            }
            message = "Не удалось загрузить каталог: ${lastError?.message ?: "нет данных"}"
            if (!background) loading = false
            markInitialLoaded(catalog = true)
            } finally {
                catalogBusy = false
            }
        }
    }


    fun refreshNewEpisodes() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api.newEpisodes(1) } }
            result.onSuccess { fresh ->
                newEpisodes = if (newEpisodesPage <= 1) {
                    fresh
                } else {
                    fresh + newEpisodes.filterNot { old -> fresh.any { it.id == old.id } }
                }
                if (newEpisodesPage <= 1) newEpisodesFinished = fresh.size < NEW_EPISODES_PAGE
            }.onFailure {
                if (newEpisodes.isEmpty()) message = "Новые эпизоды: ${it.message}"
            }
            markInitialLoaded(episodes = true)
        }
    }

    fun loadMoreNewEpisodes() {
        if (newEpisodesLoadingMore || newEpisodesFinished) return
        newEpisodesLoadingMore = true
        scope.launch {
            val next = newEpisodesPage + 1
            val result = withContext(Dispatchers.IO) { runCatching { api.newEpisodes(next) } }
            result.onSuccess { page ->
                val known = newEpisodes.map { it.id }.toSet()
                newEpisodes = newEpisodes + page.filterNot { it.id in known }
                newEpisodesPage = next
                if (page.size < NEW_EPISODES_PAGE) newEpisodesFinished = true
            }
            newEpisodesLoadingMore = false
        }
    }

    fun openEpisode(item: EpisodeItem) {
        lastEpisodeId = item.id
        navigate(Screen.EpisodeDetails(item))
        qualities = emptyList()
        qualitiesError = null
        qualitiesLoading = false
        episodeInfo = null
        episodeInfoError = null
        episodeInfoLoading = false
        episodeInfoKey = null
    }

    fun loadEpisodeInfo(item: EpisodeItem) {
        val link = item.link
        if (link.isBlank()) return
        if (episodeInfoKey == link && (episodeInfoLoading || episodeInfo != null)) return

        episodeInfoKey = link
        episodeInfo = null
        episodeInfoError = null
        episodeInfoLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.episodeInfo(link) }
            }
            result.onSuccess { episodeInfo = it }
                .onFailure { episodeInfoError = "Описание: ${it.message}" }
            episodeInfoLoading = false
        }
    }

    fun loadCalendar(force: Boolean = false) {
        if (calendarLoading || (calendarEntries.isNotEmpty() && !force)) return
        calendarLoading = true
        calendarError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.schedule() }
            }
            result.onSuccess { calendarEntries = it }
                .onFailure { calendarError = "Календарь: ${it.message}" }
            calendarLoading = false
        }
    }

    fun openCalendarEntry(entry: CalendarEntry) {
        val series = series.firstOrNull { it.id == entry.seriesId } ?: SeriesItem(
            id = entry.seriesId,
            title = entry.titleRu,
            titleOrig = entry.titleEn,
            date = entry.date,
            link = entry.seriesLink,
            rating = 0.0,
            genres = "",
            img = entry.poster,
            kind = if (entry.seriesLink.contains("/movies/")) {
                SeriesItem.Kind.MOVIE
            } else {
                SeriesItem.Kind.SERIES
            },
        )
        navigate(Screen.Details(series))
    }

    fun loadSeriesDetails(item: SeriesItem) {
        val key = item.link
        if (key.isBlank()) return
        if (seriesDetailsKey == key && (seriesDetailsLoading || seriesDetails != null)) return

        seriesDetailsKey = key
        seriesDetails = null
        seriesDetailsError = null
        seriesDetailsLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.seriesDetails(item) }
            }
            result.onSuccess { seriesDetails = it }
                .onFailure { seriesDetailsError = "Не удалось загрузить сериал: ${it.message}" }
            seriesDetailsLoading = false
        }
    }

    fun loadQualities(item: EpisodeItem) {
        if (qualitiesLoading) return
        qualitiesLoading = true
        qualitiesError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api.qualities(item.id) } }
            result.onSuccess { found ->
                qualities = found
                if (found.isEmpty()) {
                    qualitiesError = "Качества не найдены"
                } else {
                    val auto = settings.autoQuality
                    if (auto != "NONE") {
                        val key = when (auto) {
                            "SD" -> "SD"
                            "HD" -> "HD"
                            "1080", "FHD" -> "1080"
                            else -> auto
                        }
                        val match = found.firstOrNull { it.key.equals(key, true) }
                            ?: if (settings.autoPlayLow) found.firstOrNull() else null
                        if (match != null) playQuality(item, match)
                    }
                }
            }.onFailure {
                qualitiesError = when (it) {
                    is LostFilmApi.LoginRequiredException ->
                        "Зеркало ${it.host} требует входа — откройте «Войти» справа сверху"
                    else -> "Ошибка: ${it.message}"
                }
            }
            qualitiesLoading = false
        }
    }

    fun playQuality(item: EpisodeItem, quality: QualityOption) {
        if (playbackBusy || pendingPlayback != null) return
        addHistory(item)
        if (settings.autoWatched && !isWatched(item)) {
            toggleWatched(item)
        }

        val available = availablePlayers()
        val preferred = available.firstOrNull { it.path.equals(settings.defaultPlayer, true) }
        if (available.isNotEmpty() && (preferred == null || (settings.askPlayer && available.size > 1))) {
            pendingPlayback = PendingPlayback.Episode(item, quality)
            pendingPlayerTitle = quality.label
            return
        }
        startEpisode(item, quality, preferred?.path)
    }

    private fun startEpisode(item: EpisodeItem, quality: QualityOption, playerPath: String?) {
        if (playbackBusy) return
        playbackBusy = true
        message = "Готовлю поток ${quality.label}..."

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { resolveEpisodeSource(item, quality) }
            }
            result.onSuccess { source ->
                openInPlayer(source.url, playerPath)
                message = source.note
                if (settings.showWatchDialog && !settings.autoWatched) {
                    pendingWatchedDialog = item
                }
            }.onFailure {
                message = "Не удалось воспроизвести: ${it.message}"
            }
            playbackBusy = false
        }
    }

    fun loadMovieQualities(item: SeriesItem) {
        if (item.kind != SeriesItem.Kind.MOVIE) return
        val key = "${item.id}001001"
        if (movieQualitiesFor == key && (movieQualitiesLoading || movieQualities.isNotEmpty())) return

        movieQualitiesFor = key
        movieQualities = emptyList()
        movieQualitiesError = null
        movieQualitiesLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.qualities(key) }
            }
            result.onSuccess { found ->
                movieQualities = found
                if (found.isEmpty()) movieQualitiesError = "Для фильма нет полного видео"
            }.onFailure {
                movieQualitiesError = "Качества фильма: ${it.message}"
            }
            movieQualitiesLoading = false
        }
    }

    fun playMovieQuality(item: SeriesItem, quality: QualityOption) {
        if (playbackBusy || pendingPlayback != null) return

        val available = availablePlayers()
        val preferred = available.firstOrNull { it.path.equals(settings.defaultPlayer, true) }
        if (available.isNotEmpty() && (preferred == null || (settings.askPlayer && available.size > 1))) {
            pendingPlayback = PendingPlayback.Movie(item, quality)
            pendingPlayerTitle = "Фильм · ${quality.label}"
            return
        }
        startMovie(item, quality, preferred?.path)
    }

    private fun startMovie(item: SeriesItem, quality: QualityOption, playerPath: String?) {
        if (playbackBusy) return
        playbackBusy = true
        message = "Готовлю фильм: ${quality.label}..."

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { resolveMovieSource(item, quality) }
            }
            result.onSuccess { source ->
                openInPlayer(source.url, playerPath)
                message = source.note
            }.onFailure {
                message = "Не удалось воспроизвести фильм: ${it.message}"
            }
            playbackBusy = false
        }
    }

    private data class PlaySource(val url: String, val note: String)

    private fun resolveEpisodeSource(item: EpisodeItem, quality: QualityOption): PlaySource {
        val stream = runCatching { TorrServerManager.streamUrl(quality.url) }.getOrNull()
        if (stream != null && TorrServerManager.streamIsReady(stream, timeoutMs = 4_000)) {
            return PlaySource(stream, "Открываю ${quality.label} в плеере")
        }
        val direct = runCatching { api.playLinks(item.link) }.getOrDefault(emptyList())
        PlaybackSources.pickDirect(direct, quality.label)?.let { link ->
            return PlaySource(link.url, "P2P недоступен — открываю прямой файл ${link.quality}")
        }
        if (stream != null) {
            return PlaySource(stream, "Поток пока без пиров — плеер будет ждать данных")
        }
        throw IOException("не удалось подготовить поток")
    }

    private fun resolveMovieSource(item: SeriesItem, quality: QualityOption): PlaySource {
        val stream = runCatching { TorrServerManager.streamUrl(quality.url) }.getOrNull()
        if (stream != null && TorrServerManager.streamIsReady(stream, timeoutMs = 4_000)) {
            return PlaySource(stream, "Фильм готов: ${quality.label}")
        }
        val direct = runCatching { api.playLinks(item.link) }.getOrDefault(emptyList())
        PlaybackSources.pickDirect(direct, quality.label)?.let { link ->
            return PlaySource(link.url, "P2P недоступен — открываю прямой файл ${link.quality}")
        }
        if (stream != null) {
            return PlaySource(stream, "Поток пока без пиров — плеер будет ждать данных")
        }
        throw IOException("не удалось подготовить поток")
    }

    fun toggleWatched(item: EpisodeItem) {
        val newValue = !isWatched(item)
        watchedOverrides = watchedOverrides + (item.id to newValue)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.setWatched(item.id, newValue, api.findSessionId()) }
            }
            result.onSuccess { ok ->
                if (!ok) {
                    watchedOverrides = watchedOverrides - item.id
                    message = "Не удалось изменить статус"
                }
            }.onFailure {
                watchedOverrides = watchedOverrides - item.id
                message = "Ошибка: ${it.message}"
            }
        }
    }

    fun isWatched(item: EpisodeItem): Boolean = watchedOverrides[item.id] ?: item.watched

    fun movieVideoId(item: SeriesItem): String = "${item.id}001001"

    fun isMovieWatched(item: SeriesItem): Boolean = watchedOverrides[movieVideoId(item)] ?: false

    fun toggleMovieWatched(item: SeriesItem) {
        val id = movieVideoId(item)
        val newValue = !(watchedOverrides[id] ?: false)
        watchedOverrides = watchedOverrides + (id to newValue)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.setWatched(id, newValue, api.findSessionId()) }
            }
            result.onSuccess { ok ->
                if (!ok) {
                    watchedOverrides = watchedOverrides - id
                    message = "Не удалось изменить статус"
                }
            }.onFailure {
                watchedOverrides = watchedOverrides - id
                message = "Ошибка: ${it.message}"
            }
        }
    }

    fun goToSeries(item: EpisodeItem) {
        val target = series.firstOrNull { it.id == item.seriesId } ?: SeriesItem(
            id = item.seriesId,
            title = item.seriesTitleRu,
            titleOrig = item.seriesTitleEn,
            date = "",
            link = item.link.substringBefore("/season_"),
            rating = 0.0,
            genres = "",
            img = item.widePoster,
            kind = SeriesItem.Kind.SERIES,
        )
        navigate(Screen.Details(target))
    }

    fun setBackground(url: String?) {
        if (!url.isNullOrBlank()) backgroundUrl = url
    }

    private fun addHistory(item: EpisodeItem) {
        history = (listOf(item) + history.filterNot { it.id == item.id }).take(30)
    }

    fun chooseMirrorOnLogin(host: String) {
        val normalized = normalizeHost(host).ifBlank { DEFAULT_HOST }
        if (settings.host.equals(normalized, ignoreCase = true)) return
        val mirrors = (settings.mirrors + normalized).distinctBy { it.lowercase() }
        settings = settings.copy(host = normalized, mirrors = mirrors)
        SettingsStore.save(settings)
        api.clearQualitiesCache()
        message = "Зеркало: $normalized"
        loadCaptcha()
    }
    fun saveHost(host: String) {
        val normalized = normalizeHost(host).ifBlank { DEFAULT_HOST }
        val changed = !settings.host.equals(normalized, ignoreCase = true)
        val mirrors = (settings.mirrors + normalized).distinctBy { it.lowercase() }
        settings = settings.copy(host = normalized, mirrors = mirrors)
        SettingsStore.save(settings)
        if (changed) api.clearQualitiesCache()
        message = "Зеркало сохранено: ${settings.host}"
        refreshCatalog()
    }

    private fun normalizeHost(raw: String): String =
        raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .lowercase()

    private fun isHostValid(host: String): Boolean =
        host.isNotBlank() && HOST_PATTERN.matches(host)


    fun mirrorStatusOf(host: String): Boolean? = mirrorStatus[normalizeHost(host)]

    fun isMirrorChecking(host: String): Boolean = normalizeHost(host) in checkingMirrors

    fun checkMirror(host: String) {
        val normalized = normalizeHost(host)
        if (normalized.isBlank()) return
        mirrorStatus = mirrorStatus + (normalized to null)
        checkingMirrors = checkingMirrors + normalized
        scope.launch {
            val result = withContext(Dispatchers.IO) { api.checkHost(normalized) }
            val ok = result.getOrNull()?.let { it in 200..399 } == true
            mirrorStatus = mirrorStatus + (normalized to ok)
            checkingMirrors = checkingMirrors - normalized
            message = if (ok) {
                "Соединение есть: $normalized"
            } else {
                "Соединение недоступно: $normalized"
            }
        }
    }

    fun checkAllMirrors() {
        scope.launch {
            val statuses = LinkedHashMap<String, Boolean?>()
            for (mirror in settings.mirrors) {
                val normalized = normalizeHost(mirror)
                if (normalized.isBlank()) continue
                statuses[normalized] = null
                mirrorStatus = mirrorStatus + (normalized to null)
                checkingMirrors = checkingMirrors + normalized
                val result = withContext(Dispatchers.IO) { api.checkHost(normalized) }
                val ok = result.getOrNull()?.let { it in 200..399 } == true
                statuses[normalized] = ok
                mirrorStatus = mirrorStatus + (normalized to ok)
                checkingMirrors = checkingMirrors - normalized
            }

            val working = statuses.entries.firstOrNull { it.value == true }?.key
            if (working == null) {
                message = "Ни одно зеркало не ответило"
            } else if (!working.equals(settings.host, ignoreCase = true)) {
                settings = settings.copy(host = working)
                SettingsStore.save(settings)
                message = "Выбрано рабочее зеркало: $working"
                refreshCatalog()
            } else {
                message = "Текущее зеркало доступно: $working"
            }
        }
    }

    fun addMirror(host: String) {
        val normalized = normalizeHost(host)
        if (!isHostValid(normalized)) {
            message = "Некорректный адрес зеркала"
            return
        }
        if (settings.mirrors.any { it.equals(normalized, ignoreCase = true) }) {
            message = "Такое зеркало уже есть"
            return
        }
        settings = settings.copy(mirrors = settings.mirrors + normalized)
        SettingsStore.save(settings)
        message = "Зеркало добавлено: $normalized"
        checkMirror(normalized)
    }

    fun removeMirror(host: String) {
        if (settings.mirrors.size <= 1) {
            message = "Должно остаться хотя бы одно зеркало"
            return
        }
        val normalized = normalizeHost(host)
        val previousHost = settings.host
        val mirrors = settings.mirrors.filterNot { it.equals(normalized, ignoreCase = true) }
        val newHost = if (previousHost.equals(normalized, ignoreCase = true)) {
            mirrors.first()
        } else {
            previousHost
        }
        settings = settings.copy(host = newHost, mirrors = mirrors)
        SettingsStore.save(settings)
        if (!newHost.equals(previousHost, ignoreCase = true)) {
            api.clearQualitiesCache()
            refreshCatalog()
        }
        message = "Зеркало удалено"
    }

    fun setAutoMirror(value: Boolean) {
        settings = settings.copy(autoMirror = value)
        SettingsStore.save(settings)
    }

    fun setHomeRowEnabled(key: String, enabled: Boolean) {
        if (key !in app.lostfilm.desktop.core.HOME_ROW_ORDER) return
        val current = settings.homeRows
        val next = if (enabled) {
            (current + key).distinct()
        } else {
            current.filterNot { it == key }
        }
        settings = settings.copy(homeRows = next)
        SettingsStore.save(settings)
    }

    fun moveHomeRow(key: String, delta: Int) {
        val rows = settings.homeRows.toMutableList()
        val index = rows.indexOf(key)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, rows.lastIndex)
        if (target == index) return
        rows.removeAt(index)
        rows.add(target, key)
        settings = settings.copy(homeRows = rows)
        SettingsStore.save(settings)
    }

    fun setHomeRowOrder(order: List<String>) {
        val known = order.filter { it in app.lostfilm.desktop.core.HOME_ROW_ORDER }.distinct()
        if (known == settings.homeRows) return
        settings = settings.copy(homeRows = known)
        SettingsStore.save(settings)
    }

    fun setAutoQuality(value: String) {
        settings = settings.copy(autoQuality = value)
        SettingsStore.save(settings)
    }

    fun setHideWatched(value: Boolean) {
        settings = settings.copy(hideWatched = value)
        SettingsStore.save(settings)
    }

    fun setFullSeason(value: Boolean) {
        settings = settings.copy(fullSeason = value)
        SettingsStore.save(settings)
    }

    fun setCardScale(value: Float) {
        val allowed = listOf(1.0f, 0.85f, 0.75f, 0.4f)
        val snapped = allowed.minByOrNull { kotlin.math.abs(it - value) } ?: 0.85f
        settings = settings.copy(cardScale = snapped)
        SettingsStore.save(settings)
    }


    fun setImageHost(value: String) {
        settings = settings.copy(imageHost = value.trim())
        SettingsStore.save(settings)
    }

    fun setAutoPlayLow(value: Boolean) {
        settings = settings.copy(autoPlayLow = value)
        SettingsStore.save(settings)
    }

    fun setOnlyFavoriteNew(value: Boolean) {
        settings = settings.copy(onlyFavoriteNew = value)
        SettingsStore.save(settings)
    }

    fun setSortFav(value: String) {
        settings = settings.copy(sortFav = value)
        SettingsStore.save(settings)
    }

    fun setSortAll(value: String) {
        settings = settings.copy(sortAll = value)
        SettingsStore.save(settings)
    }

    fun setHideFinished(value: Boolean) {
        settings = settings.copy(hideFinished = value)
        SettingsStore.save(settings)
    }

    fun setHideNotStarted(value: Boolean) {
        settings = settings.copy(hideNotStarted = value)
        SettingsStore.save(settings)
    }

    fun setSkipDetails(value: Boolean) {
        settings = settings.copy(skipDetails = value)
        SettingsStore.save(settings)
    }

    fun setAutoWatched(value: Boolean) {
        settings = settings.copy(autoWatched = value)
        SettingsStore.save(settings)
    }

    fun setShowWatchDialog(value: Boolean) {
        settings = settings.copy(showWatchDialog = value)
        SettingsStore.save(settings)
    }

    fun setChannelOnlyFavorite(value: Boolean) {
        settings = settings.copy(channelOnlyFavorite = value)
        SettingsStore.save(settings)
    }

    fun setChannelOnlyUnwatched(value: Boolean) {
        settings = settings.copy(channelOnlyUnwatched = value)
        SettingsStore.save(settings)
    }

    fun setChserieOnlyWidePoster(value: Boolean) {
        settings = settings.copy(chserieOnlyWidePoster = value)
        SettingsStore.save(settings)
    }

    fun setChserieHideNotStarted(value: Boolean) {
        settings = settings.copy(chserieHideNotStarted = value)
        SettingsStore.save(settings)
    }

    fun setAskPlayer(value: Boolean) {
        settings = settings.copy(askPlayer = value)
        SettingsStore.save(settings)
    }

    fun downloadVlc() {
        if (vlcBusy) return
        vlcBusy = true
        vlcNote = "Определяю последнюю версию VLC..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    VlcDownloader.install(File(AppPaths.appDir, "players")) { received, total ->
                        vlcNote = if (total > 0) {
                            "Скачиваю VLC: %.1f из %.1f МБ".format(
                                received / 1048576.0,
                                total / 1048576.0,
                            )
                        } else {
                            "Скачиваю VLC: %.1f МБ".format(received / 1048576.0)
                        }
                    }
                }
            }
            result.onSuccess { exe ->
                val entry = PlayerEntry("VLC (программа)", exe.absolutePath)
                val players = (settings.players + entry).distinctBy { it.path.lowercase() }
                settings = settings.copy(players = players, defaultPlayer = exe.absolutePath)
                SettingsStore.save(settings)
                vlcNote = "VLC установлен в папку программы"
                message = "VLC загружен: ${exe.parentFile?.name}"
            }.onFailure {
                vlcNote = "Не удалось скачать VLC: ${it.message}"
            }
            vlcBusy = false
        }
    }

    private fun availablePlayers(): List<PlayerEntry> =
        settings.players.ifEmpty { PlayerRegistry.defaults() }.filter { File(it.path).isFile }

    fun playbackHint(): String {
        val available = availablePlayers()
        if (available.isEmpty()) return "Плеер не найден — добавьте его в настройках"
        val preferred = available.firstOrNull { it.path.equals(settings.defaultPlayer, true) }
            ?: return "Плеер по умолчанию не найден — появится окно выбора"
        return if (settings.askPlayer && available.size > 1) {
            "Появится окно выбора; по умолчанию ${preferred.name}"
        } else {
            "Откроется в ${preferred.name}"
        }
    }


    fun choosePlayer(path: String, dontAsk: Boolean) {
        val pending = pendingPlayback ?: return
        settings = settings.copy(
            defaultPlayer = path,
            askPlayer = if (dontAsk) false else settings.askPlayer,
        )
        SettingsStore.save(settings)
        pendingPlayback = null
        pendingPlayerTitle = ""
        when (pending) {
            is PendingPlayback.Episode -> startEpisode(pending.item, pending.quality, path)
            is PendingPlayback.Movie -> startMovie(pending.item, pending.quality, path)
            is PendingPlayback.Url -> openInPlayer(pending.url, path)
        }
    }

    fun cancelPlayerDialog() {
        pendingPlayback = null
        pendingPlayerTitle = ""
    }

    fun setDefaultPlayer(path: String) {
        if (settings.players.none { it.path.equals(path, true) }) return
        settings = settings.copy(defaultPlayer = path)
        SettingsStore.save(settings)
        message = "Плеер по умолчанию обновлён"
    }

    fun addPlayer(path: String) {
        val file = File(path)
        if (!file.isFile || !file.extension.equals("exe", true)) {
            message = "Выберите exe-файл плеера"
            return
        }
        val entry = PlayerEntry(file.nameWithoutExtension, file.absolutePath)
        val players = (settings.players + entry).distinctBy { it.path.lowercase() }
        settings = settings.copy(
            players = players,
            defaultPlayer = settings.defaultPlayer.ifBlank { entry.path },
        )
        SettingsStore.save(settings)
        message = "Плеер добавлен: ${entry.name}"
    }

    fun removePlayer(path: String) {
        val players = settings.players.filterNot { it.path.equals(path, true) }
        val default = if (!settings.defaultPlayer.equals(path, true)) {
            settings.defaultPlayer
        } else {
            players.firstOrNull { PlayerRegistry.isVlc(it) }?.path
                ?: players.firstOrNull()?.path.orEmpty()
        }
        settings = settings.copy(players = players, defaultPlayer = default)
        SettingsStore.save(settings)
        message = "Плеер удалён"
    }

    fun refreshPlayers() {
        val players = (settings.players + PlayerRegistry.defaults())
            .distinctBy { it.path.lowercase() }
        val default = settings.defaultPlayer.takeIf { current ->
            players.any { it.path.equals(current, true) }
        } ?: players.firstOrNull { PlayerRegistry.isVlc(it) }?.path
            ?: players.firstOrNull()?.path.orEmpty()
        settings = settings.copy(players = players, defaultPlayer = default)
        SettingsStore.save(settings)
        message = "Найдено плееров: ${players.size}"
    }

    fun loadCaptcha() {
        captchaBytes = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api.loadCaptcha() } }
            result.onSuccess { captchaBytes = it }
                .onFailure { loginError = "Не удалось получить капчу: ${it.message}" }
        }
    }

    fun login(email: String, password: String, captcha: String, remember: Boolean) {
        loginError = null
        if (email.isBlank() || password.isBlank()) {
            loginError = "Введите e-mail и пароль"
            return
        }
        loginBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.login(email.trim(), password, captcha.trim()) }
            }
            result.onSuccess { response ->
                if (response.ok) {
                    if (remember) {
                        CredentialsStore.save(email.trim(), password)
                        savedCredentials = SavedCredentials(email.trim(), password)
                    }
                    settings = settings.copy(
                        logged = true,
                        userName = response.userName ?: email.trim(),
                    )
                    SettingsStore.save(settings)
                    refreshAvatar()
                    message = "Вы вошли как ${settings.userName}"
                    catalogLoaded = false
                    episodesLoaded = false
                    homeLoading = true
                    goHome()
                    refreshCatalog()
                    refreshNewEpisodes()
                } else {
                    loginError = response.message ?: "Не удалось войти"
                    if (response.needCaptcha) loadCaptcha()
                }
            }.onFailure {
                loginError = "Ошибка входа: ${it.message}"
            }
            loginBusy = false
        }
    }

    fun saveCredentials(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            message = "Введите e-mail и пароль"
            return
        }
        CredentialsStore.save(email.trim(), password)
        savedCredentials = SavedCredentials(email.trim(), password)
        message = "Почта и пароль сохранены в файл"
    }

    fun clearCredentials() {
        CredentialsStore.clear()
        savedCredentials = SavedCredentials()
        message = "Сохранённые почта и пароль удалены"
    }

    fun searchSite(query: String) {
        searchJob?.cancel()
        searchError = null
        val normalized = query.trim()
        if (normalized.isBlank()) {
            searchResults = emptyList()
            searchLoading = false
            return
        }

        searchLoading = true
        searchResults = emptyList()
        searchJob = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api.search(normalized) } }
            result.onSuccess { found ->
                searchResults = found
                if (found.isEmpty()) {
                    searchError = "Ничего не найдено"
                } else {
                    rememberSearchQuery(normalized)
                }
            }.onFailure {
                searchError = "Ошибка поиска: ${it.message}"
            }
            searchLoading = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchResults = emptyList()
        searchError = null
        searchLoading = false
    }

    fun watch(item: SeriesItem) {
        watchBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.playLinks(item.link) }
            }
            result.onSuccess { links ->
                val chosen = links.firstOrNull { it.url.contains("_720p", true) }
                    ?: links.firstOrNull()
                if (chosen == null) {
                    message = "На странице не найдено видео"
                } else {
                    val available = availablePlayers()
                    val preferred = available.firstOrNull { it.path.equals(settings.defaultPlayer, true) }
                    if (available.isNotEmpty() &&
                        (preferred == null || (settings.askPlayer && available.size > 1))
                    ) {
                        pendingPlayback = PendingPlayback.Url(chosen.url, "Видео")
                        pendingPlayerTitle = "Видео"
                    } else {
                        openInPlayer(chosen.url, preferred?.path)
                        message = "Видео готово: ${chosen.quality.ifBlank { "SD" }}"
                    }
                }
            }.onFailure {
                message = "Ошибка видео: ${it.message}"
            }
            watchBusy = false
        }
    }

    private fun openInPlayer(url: String, playerPath: String? = null): Boolean {
        val available = availablePlayers()
        val player = available.firstOrNull { it.path.equals(playerPath, true) }
            ?: available.firstOrNull { it.path.equals(settings.defaultPlayer, true) }
            ?: available.firstOrNull()

        if (player != null) {
            val command = mutableListOf(player.path)
            if (PlayerRegistry.isVlc(player)) {
                command += listOf("--no-one-instance", "--no-video-title-show", "--network-caching=3000")
            }
            command += url
            val started = runCatching { ProcessBuilder(command).start() }.isSuccess
            if (started) return true
        }
        return app.lostfilm.desktop.platform.openInBrowser(url)
    }

    fun logout() {
        settings = settings.copy(logged = false, userName = "")
        avatarPath = null
        SettingsStore.save(settings)
        message = "Вы вышли из аккаунта"
    }

    fun isFavorite(item: SeriesItem): Boolean {
        val key = item.link.trim().trimEnd('/')
        return if (favoriteLinks.isNotEmpty()) key in favoriteLinks else !item.notFavorited
    }

    fun follow(item: SeriesItem) {
        if (!settings.logged) {
            message = "Сначала войдите в аккаунт"
            navigate(Screen.Login)
            return
        }
        if (followBusy) return
        followBusy = true
        scope.launch {
            val wasFavorite = isFavorite(item)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val session = api.findSessionId()
                    if (session.isBlank()) "" else api.setFollow(item.id, session)
                }
            }
            result.onSuccess { state ->
                val nowFavorite = when (state) {
                    "on" -> true
                    "off" -> false
                    else -> !wasFavorite
                }
                message = if (nowFavorite) {
                    "Добавлено в избранное: ${item.title}"
                } else {
                    "Убрано из избранного: ${item.title}"
                }
                series = series.map {
                    if (it.id == item.id) it.copy(notFavorited = !nowFavorite) else it
                }
                movies = movies.map {
                    if (it.id == item.id) it.copy(notFavorited = !nowFavorite) else it
                }
                val key = item.link.trim().trimEnd('/')
                favoriteLinks = if (nowFavorite) favoriteLinks + key else favoriteLinks - key
            }.onFailure {
                message = "Ошибка: ${it.message}"
            }
            followBusy = false
        }
    }

    fun dismissWatchedDialog(markWatched: Boolean) {
        val item = pendingWatchedDialog ?: return
        pendingWatchedDialog = null
        if (markWatched && !isWatched(item)) {
            toggleWatched(item)
        }
    }

    fun clearMessage() {
        message = null
    }
}