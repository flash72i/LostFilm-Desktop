package app.lostfilm.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.core.PlayerEntry
import app.lostfilm.desktop.core.PlayerRegistry
import app.lostfilm.desktop.platform.openFolder
import app.lostfilm.desktop.platform.openInBrowser
import app.lostfilm.desktop.ui.AppModel
import app.lostfilm.desktop.ui.Screen
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val QUALITY_ITEMS = listOf(
    "NONE" to "Отключено",
    "SD" to "480p",
    "HD" to "720p",
    "1080" to "1080p",
)
private val SORT_ITEMS = listOf(
    "title" to "Алфавиту",
    "rating" to "Рейтингу",
    "used" to "Использованию",
)
private val SCALE_ITEMS = listOf(
    1.0f to "Большие",
    0.85f to "Средние",
    0.75f to "Маленькие",
    0.4f to "Микро",
)

@Composable
fun SettingsScreen(model: AppModel) {
    var newMirror by remember { mutableStateOf("") }
    var hostInput by remember(model.settings.host) { mutableStateOf(model.settings.host) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    LaunchedEffect(model.settings.logged, model.avatarPath) {
        if (model.settings.logged && model.avatarPath == null) model.refreshAvatar()
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                    Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                    else -> false
                }
            }
            .padding(start = 28.dp, top = 28.dp, bottom = 28.dp, end = 52.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            model.settings.userName.ifBlank { "Настройки" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        // Выбор зеркала — один список, без деления на «официальное/вручную»
        SettingsSection("Аккаунт") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsAvatar(model)
                Column {
                    Text(
                        "LOSTFILM",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (model.settings.logged) "Вы вошли как ${model.settings.userName}" else "Вы не вошли в аккаунт",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (model.settings.logged) {
                    OutlinedButton(onClick = { model.logout() }) { Text("Выйти") }
                } else {
                    Button(onClick = { model.navigate(Screen.Login) }) { Text("Войти") }
                }
            }
        }

        SettingsSection("Выбор зеркала") {
            Text(
                "Текущее: ${model.settings.host}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            model.settings.mirrors.forEach { mirror ->
                val selected = mirror.equals(model.settings.host, ignoreCase = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChipSimple(
                        text = mirror,
                        selected = selected,
                        onClick = { model.saveHost(mirror) },
                    )
                    when {
                        model.isMirrorChecking(mirror) -> Text(
                            "проверяю…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        model.mirrorStatusOf(mirror) == true -> Text(
                            "доступно",
                            color = Color(0xFF66BB6A),
                        )
                        model.mirrorStatusOf(mirror) == false -> Text(
                            "недоступно",
                            color = Color(0xFFEF5350),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { model.checkMirror(mirror) }) { Text("Проверить") }
                    TextButton(
                        onClick = { model.removeMirror(mirror) },
                        enabled = model.settings.mirrors.size > 1,
                    ) { Text("Удалить") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { model.checkAllMirrors() }) { Text("Проверить все") }
                OutlinedButton(onClick = { openInBrowser(model.baseUrl()) }) { Text("Открыть сайт") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newMirror,
                    onValueChange = { newMirror = it },
                    label = { Text("Новое зеркало") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        model.addMirror(newMirror)
                        newMirror = ""
                    },
                    enabled = newMirror.isNotBlank(),
                ) { Text("Добавить") }
            }
            ToggleRow(
                "Автоматически переключать зеркала",
                model.settings.autoMirror,
                model::setAutoMirror,
            )
            Text(
                "Текущий домен: ${model.settings.host}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Настройки воспроизведения — как PlaySetting
        SettingsSection("Настройки воспроизведения") {
            ToggleRow(
                "Полный сезон",
                model.settings.fullSeason,
                model::setFullSeason,
            )
            Text(
                "Пытаться брать ссылку на полный сезон, вместо ссылки на серию",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Авто воспроизведение", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUALITY_ITEMS.forEach { (value, label) ->
                    FilterChipSimple(
                        text = label,
                        selected = model.settings.autoQuality == value,
                        onClick = { model.setAutoQuality(value) },
                    )
                }
            }
            ToggleRow(
                "Воспроизводить любое",
                model.settings.autoPlayLow,
                model::setAutoPlayLow,
                enabled = model.settings.autoQuality != "NONE",
            )
            Text(
                "Если отсутствует выбранное, то играем любое имеющееся",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Запрос после запуска", style = MaterialTheme.typography.titleSmall)
            ToggleRow(
                "Спрашивать «Отметить просмотренной?»",
                model.settings.showWatchDialog,
                model::setShowWatchDialog,
                enabled = !model.settings.autoWatched,
            )
            Text(
                "Всегда показывать этот запрос или не показывать совсем",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow(
                "Авто отметка о просмотре",
                model.settings.autoWatched,
                model::setAutoWatched,
            )
            Text(
                "Помечать серию просмотренной сразу, без запроса",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Плеер по умолчанию", style = MaterialTheme.typography.titleSmall)
            val players = model.settings.players
            val selected = players.firstOrNull {
                it.path.equals(model.settings.defaultPlayer, true)
            }
            Text(
                "Сейчас: ${selected?.name ?: "не выбран"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            players.forEach { player ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerIcon(player)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(player.name)
                        Text(
                            player.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (selected?.path == player.path) {
                        Text("Выбран", color = Color(0xFF66BB6A))
                    } else {
                        OutlinedButton(onClick = { model.setDefaultPlayer(player.path) }) {
                            Text("Выбрать")
                        }
                    }
                    TextButton(onClick = { model.removePlayer(player.path) }) { Text("Удалить") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val chooser = JFileChooser()
                    chooser.dialogTitle = "Выберите плеер"
                    chooser.fileFilter = FileNameExtensionFilter("Программы (*.exe)", "exe")
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        model.addPlayer(chooser.selectedFile.absolutePath)
                    }
                }) { Text("Добавить плеер") }
                OutlinedButton(onClick = { model.refreshPlayers() }) { Text("Найти плееры") }
                OutlinedButton(
                    onClick = { model.downloadVlc() },
                    enabled = !model.vlcBusy,
                ) {
                    Text(if (model.vlcBusy) "Загрузка VLC..." else "Скачать VLC")
                }
            }
            model.vlcNote?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ToggleRow(
                "Спрашивать плеер при запуске",
                model.settings.askPlayer,
                model::setAskPlayer,
            )
        }

        // Ряды главного экрана — карточки с перетаскиванием
        SettingsSection("Ряды главного экрана") {
            Text(
                "Перетащите карточку мышью вверх или вниз — порядок сохранится сразу.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var dragKey by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val enabledRows = model.settings.homeRows
            val rows = enabledRows + app.lostfilm.desktop.core.HOME_ROW_ORDER.filterNot { it in enabledRows }
            rows.forEach { key ->
                val enabled = key in enabledRows
                val isDragging = dragKey == key
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D22)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .zIndex(if (isDragging) 10f else 0f)
                        .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                        .then(
                            if (enabled) {
                                Modifier.pointerInput(key, enabledRows) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragKey = key
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                        },
                                        onDragEnd = {
                                            val rowPx = 58.dp.toPx()
                                            val steps = (dragOffset / rowPx).roundToInt()
                                            val current = model.settings.homeRows
                                            val from = current.indexOf(key)
                                            val to = (from + steps).coerceIn(0, current.lastIndex)
                                            if (from >= 0 && to != from) {
                                                val next = current.toMutableList()
                                                next.removeAt(from)
                                                next.add(to, key)
                                                model.setHomeRowOrder(next)
                                            }
                                            dragKey = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragKey = null
                                            dragOffset = 0f
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "≡",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) Color(0xFF8A9099) else Color(0xFF4E535B),
                        )
                        Text(
                            app.lostfilm.desktop.core.HOME_ROW_TITLES[key] ?: key,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color(0xFF6E737C)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { model.setHomeRowEnabled(key, it) },
                        )
                    }
                }
            }
        }
        SettingsSection("Настройки интерфейса") {
            Text("Главный экран · Новые эпизоды", style = MaterialTheme.typography.titleSmall)
            ToggleRow(
                "Только избранное",
                model.settings.onlyFavoriteNew,
                model::setOnlyFavoriteNew,
            )
            ToggleRow(
                "Скрыть просмотренные",
                model.settings.hideWatched,
                model::setHideWatched,
            )
            Text("Главный экран · Избранные сериалы", style = MaterialTheme.typography.titleSmall)
            Text("Сортировать список сериалов по:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SORT_ITEMS.forEach { (value, label) ->
                    FilterChipSimple(
                        text = label,
                        selected = model.settings.sortFav == value,
                        onClick = { model.setSortFav(value) },
                    )
                }
            }
            ToggleRow(
                "Скрыть завершенные",
                model.settings.hideFinished,
                model::setHideFinished,
            )
            Text("Главный экран · Все сериалы", style = MaterialTheme.typography.titleSmall)
            Text("Сортировать список сериалов по:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SORT_ITEMS.forEach { (value, label) ->
                    FilterChipSimple(
                        text = label,
                        selected = model.settings.sortAll == value,
                        onClick = { model.setSortAll(value) },
                    )
                }
            }
            Text("Главный экран · Новые сериалы", style = MaterialTheme.typography.titleSmall)
            ToggleRow(
                "Скрыть не начавшиеся сериалы",
                model.settings.hideNotStarted,
                model::setHideNotStarted,
            )

            Text("Размер карточек", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SCALE_ITEMS.forEach { (value, label) ->
                    FilterChipSimple(
                        text = label,
                        selected = kotlin.math.abs(model.settings.cardScale - value) < 0.01f,
                        onClick = { model.setCardScale(value) },
                    )
                }
            }
            Text(
                "Предпросмотр:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val previewItems = remember(model.series, model.movies) {
                    (model.series + model.movies).take(3)
                }
                if (previewItems.isEmpty()) {
                    repeat(3) {
                        Card(
                            modifier = Modifier
                                .width((90 * model.settings.cardScale).dp)
                                .height((130 * model.settings.cardScale).dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                } else {
                    previewItems.forEach { preview ->
                        SettingsCardPreview(model, preview, model.settings.cardScale)
                    }
                }
            }

            Text("Карточка сериала", style = MaterialTheme.typography.titleSmall)
            ToggleRow(
                "Пропускать карточку сериала",
                model.settings.skipDetails,
                model::setSkipDetails,
            )
        }

        SettingsSection("Данные") {
            Text(
                model.dataDir().absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { openFolder(model.dataDir().absolutePath) }) {
                Text("Открыть папку")
            }
        }

        OutlinedButton(onClick = { model.navigate(Screen.About) }) {
            Text("О приложении")
        }
        Spacer(Modifier.height(16.dp))
    }

    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(top = 16.dp, bottom = 16.dp, end = 12.dp)
            .width(14.dp),
        style = ScrollbarStyle(
            minimalHeight = 48.dp,
            thickness = 12.dp,
            shape = RoundedCornerShape(6.dp),
            hoverDurationMillis = 0,
            unhoverColor = Color(0xFF9AA0A8),
            hoverColor = Color(0xFFE4E8EE),
        ),
    )
    }
}

@Composable
private fun SettingsCardPreview(
    model: AppModel,
    item: app.lostfilm.desktop.data.SeriesItem,
    scale: Float,
) {
    val url = remember(item.id) { model.seriesPoster(item) }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            app.lostfilm.desktop.data.ImageCache.load(url, model.baseUrl() + "/")
        }
    }

    Card(
        modifier = Modifier
            .width((90 * scale).dp)
            .height((130 * scale).dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF232327) else Color.Transparent)
            .border(
                2.dp,
                if (focused) Color(0xFF4FC3F7) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .focusable(enabled = enabled)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && enabled) {
                    onChange(!checked)
                    true
                } else {
                    false
                }
            }
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color(0xFF6E737C),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (checked) "Вкл" else "Выкл",
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> Color(0xFF6E737C)
                checked -> Color(0xFF66BB6A)
                else -> Color(0xFFB9BFC8)
            },
            modifier = Modifier.padding(end = 6.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun FilterChipSimple(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF745EC5) else Color(0xFF26272E))
            .border(
                2.dp,
                if (focused) Color(0xFF4FC3F7) else Color.Transparent,
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    onClick(); true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PlayerIcon(player: PlayerEntry) {
    var icon by remember(player.path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(player.path) {
        icon = withContext(Dispatchers.IO) {
            PlayerRegistry.iconFile(player)?.let { file ->
                ImageIO.read(file)?.toComposeImageBitmap()
            }
        }
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val image = icon
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = player.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun SettingsAvatar(model: AppModel) {
    val path = model.avatarPath
    val url = remember(path) { path?.let { model.imageUrl(it) }.orEmpty() }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                app.lostfilm.desktop.data.ImageCache.load(url, model.baseUrl() + "/")
            }
        }
    }
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = model.settings.userName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                model.settings.userName.take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
