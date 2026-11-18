package app.lostfilm.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.lostfilm.desktop.ui.App
import app.lostfilm.desktop.ui.AppModel
import app.lostfilm.desktop.ui.theme.LostFilmTheme
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import kotlinx.coroutines.delay

private interface DwmApi : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, attr: Int, value: Pointer, size: Int): Int
}

private interface User32Api : Library {
    fun FindWindowW(lpClassName: WString?, lpWindowName: WString?): Pointer?
}

/** Закруглённые углы окна в Windows 11 (DWMWA_WINDOW_CORNER_PREFERENCE). */
private fun applyRoundedCorners(title: String) {
    runCatching {
        val user32 = Native.load("user32", User32Api::class.java)
        val dwm = Native.load("dwmapi", DwmApi::class.java)
        val hwnd = user32.FindWindowW(null, WString(title)) ?: return
        val preference = Memory(4).apply { setInt(0, 2) }
        dwm.DwmSetWindowAttribute(hwnd, 33, preference, 4)
    }
}

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
    val model = remember { AppModel() }

    Window(
        onCloseRequest = ::exitApplication,
        title = app.lostfilm.desktop.core.APP_TITLE,
        state = windowState,
        icon = painterResource("app.png"),
        undecorated = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                model.goBack()
                true
            } else {
                false
            }
        },
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = java.awt.Dimension(1100, 700)
            delay(300)
            applyRoundedCorners(app.lostfilm.desktop.core.APP_TITLE)
        }
        LostFilmTheme {
            App(model = model, window = window, onClose = ::exitApplication)
        }
    }
}

