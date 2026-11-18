package app.lostfilm.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lostfilm.desktop.data.ImageCache
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.lostfilm.desktop.ui.screens.AboutScreen
import app.lostfilm.desktop.ui.screens.CalendarScreenNew
import app.lostfilm.desktop.ui.screens.DetailsScreen
import app.lostfilm.desktop.ui.screens.EpisodeDetailsScreen
import app.lostfilm.desktop.ui.screens.FiltersScreenNew
import app.lostfilm.desktop.ui.screens.HomeScreen
import app.lostfilm.desktop.ui.screens.LoginScreen
import app.lostfilm.desktop.ui.screens.SearchScreen
import app.lostfilm.desktop.ui.screens.SettingsScreen

@Composable
fun App(window: ComposeWindow, model: AppModel, onClose: () -> Unit) {

    LaunchedEffect(Unit) {
        model.bootstrap()
    }

    DisposableEffect(window) {
        val listener = object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
                    model.goBack()
                }
            }
        }
        window.addKeyListener(listener)
        onDispose { window.removeKeyListener(listener) }
    }

    CompositionLocalProvider(LocalIndication provides remember { TvIndication() }) {
        val rootFocusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> rootFocusManager.moveFocus(FocusDirection.Left)
                        Key.DirectionRight -> rootFocusManager.moveFocus(FocusDirection.Right)
                        Key.DirectionUp -> rootFocusManager.moveFocus(FocusDirection.Up)
                        Key.DirectionDown -> rootFocusManager.moveFocus(FocusDirection.Down)
                        Key.Escape -> {
                            if (model.screen != Screen.Home) model.goBack()
                            true
                        }
                        else -> return@onKeyEvent false
                    }
                    true
                },
        ) {
        WindowTitleBar(window, onClose)
        TopBar(model)
        Box(Modifier.weight(1f)) {
            when (val screen = model.screen) {
                Screen.Home -> HomeScreen(model)
                is Screen.Details -> DetailsScreen(model, screen.item)
                is Screen.EpisodeDetails -> EpisodeDetailsScreen(model, screen.item)
                Screen.Search -> SearchScreen(model)
                Screen.Calendar -> CalendarScreenNew(model)
                Screen.Filters -> FiltersScreenNew(model)
                Screen.Settings -> SettingsScreen(model)
                Screen.Login -> LoginScreen(model)
                Screen.About -> AboutScreen(model)
            }
        }
        model.message?.let { text ->
            MessageBar(text) { model.clearMessage() }
        }
        }
    }
    PlayerPickerDialog(model)
    WatchedDialog(model)
}

@Composable
private fun WindowTitleBar(window: ComposeWindow, onClose: () -> Unit) {
    var dragStart by remember { mutableStateOf<Point?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF262A31))
            .border(width = 1.dp, color = Color(0xFF343943), shape = RectangleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragStart = MouseInfo.getPointerInfo()?.location
                        },
                        onDragEnd = { dragStart = null },
                        onDragCancel = { dragStart = null },
                        onDrag = { change, _ ->
                            val start = dragStart ?: return@detectDragGestures
                            val current = MouseInfo.getPointerInfo()?.location
                                ?: return@detectDragGestures
                            window.location = Point(
                                window.x + current.x - start.x,
                                window.y + current.y - start.y,
                            )
                            dragStart = current
                            change.consume()
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                app.lostfilm.desktop.core.APP_TITLE,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFD8D8DC),
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        WindowControl("—") { window.extendedState = Frame.ICONIFIED }
        WindowControl("□") {
            window.extendedState = if (window.extendedState and Frame.MAXIMIZED_BOTH != 0) {
                Frame.NORMAL
            } else {
                Frame.MAXIMIZED_BOTH
            }
        }
        WindowControl("✕", close = true, onClick = onClose)
    }
}

@Composable
private fun WindowControl(label: String, close: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (close) Color(0xFFFF7A7A) else Color(0xFFD8D8DC),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun TopBar(model: AppModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconWithHint("Главная") {
            RoundIconButton(home = true) { model.refreshFromHome() }
        }
        IconWithHint("Поиск") {
            RoundIconButton(search = true) { model.navigate(Screen.Search) }
        }
        IconWithHint("Календарь") {
            RoundIconButton(calendar = true) { model.navigate(Screen.Calendar) }
        }
        IconWithHint("Фильтры") {
            RoundIconButton(filter = true) { model.navigate(Screen.Filters) }
        }

        Spacer(Modifier.weight(1f))

        IconWithHint("Настройки") {
            ProfileButton(model)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconWithHint(hint: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(color = Color(0xFF2A2B31), shape = RoundedCornerShape(6.dp)) {
                Text(
                    hint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        delayMillis = 400,
        content = content,
    )
}

@Composable
private fun RoundIconButton(
    home: Boolean = false,
    search: Boolean = false,
    calendar: Boolean = false,
    filter: Boolean = false,
    settings: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val active = hovered || focused
    val background by animateColorAsState(
        if (active) Color(0xFF353B48) else Color(0xFF26272E),
        animationSpec = tween(120),
        label = "topButtonBackground",
    )
    val ring by animateColorAsState(
        when {
            focused -> Color(0xFF4FC3F7)
            hovered -> Color(0xFF4FC3F7).copy(alpha = 0.65f)
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "topButtonRing",
    )
    val glow by animateDpAsState(
        if (active) 14.dp else 0.dp,
        animationSpec = tween(140),
        label = "topButtonGlow",
    )
    val iconColor by animateColorAsState(
        if (active) Color.White else Color(0xFFE9E9ED),
        animationSpec = tween(120),
        label = "topButtonIcon",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = glow,
                shape = CircleShape,
                clip = false,
                ambientColor = Color(0xFF4FC3F7),
                spotColor = Color(0xFF4FC3F7),
            )
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .border(2.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val c = iconColor
            when {
                home -> {
                    drawLine(c, Offset(size.width * 0.12f, size.height * 0.47f), Offset(size.width * 0.5f, size.height * 0.14f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.5f, size.height * 0.14f), Offset(size.width * 0.88f, size.height * 0.47f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawRoundRect(c, topLeft = Offset(size.width * 0.26f, size.height * 0.45f), size = Size(size.width * 0.48f, size.height * 0.43f), cornerRadius = CornerRadius(1.5.dp.toPx()), style = stroke)
                    drawLine(c, Offset(size.width * 0.44f, size.height * 0.88f), Offset(size.width * 0.44f, size.height * 0.66f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.56f, size.height * 0.88f), Offset(size.width * 0.56f, size.height * 0.66f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.44f, size.height * 0.66f), Offset(size.width * 0.56f, size.height * 0.66f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                }
                search -> {
                    drawCircle(c, radius = size.minDimension * 0.3f, center = Offset(size.width * 0.45f, size.height * 0.42f), style = stroke)
                    drawLine(c, Offset(size.width * 0.65f, size.height * 0.62f), Offset(size.width * 0.88f, size.height * 0.85f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                }
                calendar -> {
                    drawRoundRect(c, topLeft = Offset(size.width * 0.12f, size.height * 0.2f), size = Size(size.width * 0.76f, size.height * 0.68f), cornerRadius = CornerRadius(2.dp.toPx()), style = stroke)
                    drawLine(c, Offset(size.width * 0.12f, size.height * 0.38f), Offset(size.width * 0.88f, size.height * 0.38f), strokeWidth = 1.8.dp.toPx())
                    drawLine(c, Offset(size.width * 0.3f, size.height * 0.12f), Offset(size.width * 0.3f, size.height * 0.28f), strokeWidth = 1.8.dp.toPx())
                    drawLine(c, Offset(size.width * 0.7f, size.height * 0.12f), Offset(size.width * 0.7f, size.height * 0.28f), strokeWidth = 1.8.dp.toPx())
                }
                filter -> {
                    drawLine(c, Offset(size.width * 0.12f, size.height * 0.3f), Offset(size.width * 0.88f, size.height * 0.3f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.22f, size.height * 0.5f), Offset(size.width * 0.78f, size.height * 0.5f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.35f, size.height * 0.7f), Offset(size.width * 0.65f, size.height * 0.7f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                }
                settings -> {
                    drawCircle(c, radius = size.minDimension * 0.28f, style = stroke)
                    drawCircle(c, radius = size.minDimension * 0.42f, style = Stroke(width = 1.8.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun ProfileButton(model: AppModel) {
    var focused by remember { mutableStateOf(false) }
    val onClick = { model.navigate(Screen.Settings) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .border(2.dp, if (focused) Color(0xFF4FC3F7) else Color.Transparent, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gear = Color(0xFFB9BDC6)
            val stroke = 1.6.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = if (model.settings.logged) {
                size.minDimension / 2f - 3.dp.toPx()
            } else {
                size.minDimension * 0.26f
            }
            drawCircle(gear, radius = radius, center = Offset(cx, cy), style = Stroke(stroke))
            repeat(8) { index ->
                val angle = Math.toRadians(index * 45.0)
                val dx = kotlin.math.cos(angle).toFloat()
                val dy = kotlin.math.sin(angle).toFloat()
                val inner = radius + 1.dp.toPx()
                val outer = radius + 3.dp.toPx()
                drawLine(
                    color = gear,
                    start = Offset(cx + dx * inner, cy + dy * inner),
                    end = Offset(cx + dx * outer, cy + dy * outer),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        if (model.settings.logged) {
            UserAvatar(model)
        }
    }
}

@Composable
private fun UserAvatar(model: AppModel) {
    if (!model.settings.logged) return
    val path = model.avatarPath
    val url = remember(path) { path?.let { model.imageUrl(it) }.orEmpty() }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                ImageCache.load(url, model.baseUrl() + "/")
            }
        }
    }

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
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
                model.settings.userName.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun NavItem(text: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                    Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                    Key.Enter -> onClick()
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .border(
                2.dp,
                if (focused) Color(0xFF4FC3F7) else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun MessageBar(text: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text("Закрыть")
            }
        }
    }
}

@Composable
private fun WatchedDialog(model: AppModel) {
    val item = model.pendingWatchedDialog ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { model.dismissWatchedDialog(false) },
        title = { Text("Отметить просмотренной?") },
        text = {
            Text("${item.seriesTitleRu} · ${item.displayTitle}\nПометить серию как просмотренную?")
        },
        confirmButton = {
            TextButton(onClick = { model.dismissWatchedDialog(true) }) {
                Text("Да, просмотрено")
            }
        },
        dismissButton = {
            TextButton(onClick = { model.dismissWatchedDialog(false) }) {
                Text("Нет")
            }
        },
    )
}

