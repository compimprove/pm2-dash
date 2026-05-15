package org.compi.pm2dash

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import org.compi.pm2dash.data.createProcessGroupsRepository
import org.compi.pm2dash.data.createPm2Repository
import java.awt.Color
import java.awt.Taskbar
import javax.imageio.ImageIO

fun main() = application {
    Window(
        onCloseRequest = {
            exitApplication()
        },
        title = "",
        state = WindowState(
            placement = WindowPlacement.Maximized,
        ),
    ) {
        MacOsDarkTitleBar()
        App(
            repository = createPm2Repository(),
            processGroupsRepository = createProcessGroupsRepository(),
        )
    }
}

@Suppress("MagicNumber")
@Composable
private fun FrameWindowScope.MacOsDarkTitleBar() {
    DisposableEffect(window) {
        val rootPane = window.rootPane
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        rootPane.putClientProperty("apple.awt.fullWindowContent", false)
        rootPane.putClientProperty("apple.awt.windowTitleVisible", true)
        window.background = Color(0x0B, 0x10, 0x16)
        javaClass.getResourceAsStream("/pm2dash.png")?.use { stream ->
            val image = ImageIO.read(stream)
            window.iconImage = image
            if (Taskbar.isTaskbarSupported()) {
                runCatching { Taskbar.getTaskbar().iconImage = image }
            }
        }

        onDispose { }
    }
}
