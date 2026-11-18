package app.lostfilm.desktop.ui

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val FocusCyan = Color(0xFF4FC3F7)
private val ChipIdle = Color(0xFF26272E)
private val ChipActive = Color(0xFF745EC5)

/**
 * Колесо над горизонтальным рядом всегда листает ряд и никогда — страницу.
 * Промах между карточками больше не «проваливает» скролл вниз.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.rowWheel(
    listState: LazyListState,
    scope: kotlinx.coroutines.CoroutineScope,
): Modifier = onPointerEvent(PointerEventType.Scroll) { event ->
    val change = event.changes.firstOrNull() ?: return@onPointerEvent
    val delta = if (change.scrollDelta.x != 0f) change.scrollDelta.x else change.scrollDelta.y
    if (delta != 0f) {
        scope.launch { listState.scrollBy(delta * 140f) }
        event.changes.forEach { it.consume() }
    }
}

/**
 * Широкая полоса прокрутки, отодвинутая от правого края окна.
 * Колесо над полосой листает страницу. Клик/перетаскивание — тоже.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PageScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
            .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val delta = change.scrollDelta.y
                if (delta != 0f) {
                    scope.launch {
                        state.scrollBy(delta * 220f)
                    }
                    event.changes.forEach { it.consume() }
                }
            },
    ) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.fillMaxHeight(),
            style = ScrollbarStyle(
                minimalHeight = 64.dp,
                thickness = 18.dp,
                shape = RoundedCornerShape(9.dp),
                hoverDurationMillis = 0,
                unhoverColor = Color(0xFF9AA0A8),
                hoverColor = Color(0xFFE4E8EE),
            ),
        )
    }
}

/** Чип, работающий с мышью и клавиатурой (Enter). */
@Composable
fun FocusChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) ChipActive else ChipIdle)
            .border(
                2.dp,
                if (focused) FocusCyan else Color.Transparent,
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.DirectionCenter -> {
                        onClick()
                        true
                    }
                    Key.DirectionLeft -> {
                        focusManager.moveFocus(FocusDirection.Left)
                        true
                    }
                    Key.DirectionRight -> {
                        focusManager.moveFocus(FocusDirection.Right)
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}
