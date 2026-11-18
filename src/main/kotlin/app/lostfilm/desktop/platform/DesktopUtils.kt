package app.lostfilm.desktop.platform

import java.awt.Desktop
import java.net.URI

fun openInBrowser(url: String): Boolean = runCatching {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
        true
    } else {
        false
    }
}.getOrDefault(false)

fun openFolder(path: String): Boolean = runCatching {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(java.io.File(path))
        true
    } else {
        false
    }
}.getOrDefault(false)
