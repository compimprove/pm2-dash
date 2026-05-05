package org.compi.people_search

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val server = startServer()

    Window(
        onCloseRequest = {
            server.close()
            exitApplication()
        },
        title = "People Search",
    ) {
        App()
    }
}
