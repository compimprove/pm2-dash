package org.compi.pm2dash.data

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.compi.pm2dash.model.DashboardErrorKind
import org.compi.pm2dash.model.LogChannel
import org.compi.pm2dash.model.ProcessLogsState

class Pm2RepositorySupportTest {
    @Test
    fun `mapCommandFailure detects missing pm2`() {
        val error = mapCommandFailure(
            CommandResult(
                stdout = "",
                stderr = "zsh: command not found: pm2",
                exitCode = 127,
            ),
        )

        assertEquals(DashboardErrorKind.Pm2NotInstalled, error.kind)
    }

    @Test
    fun `readLogSnapshot returns missing when neither pm2 log file exists`() {
        val logsDirectory = Files.createTempDirectory("pm2-dash-test")

        val state = readLogSnapshot("user-service", logsDirectory)

        assertIs<ProcessLogsState.Missing>(state)
    }

    @Test
    fun `readLogSnapshot combines stdout and stderr entries`() {
        val logsDirectory = Files.createTempDirectory("pm2-dash-test")
        logsDirectory.resolve("user-service-out.log").writeText("online\nhealthy\n")
        logsDirectory.resolve("user-service-error.log").writeText("warn\n")

        val state = readLogSnapshot("user-service", logsDirectory, maxEntries = 10)

        val ready = assertIs<ProcessLogsState.Ready>(state)
        assertEquals(3, ready.entries.size)
        assertTrue(ready.entries.any { it.channel == LogChannel.Stdout && it.message == "online" })
        assertTrue(ready.entries.any { it.channel == LogChannel.Stderr && it.message == "warn" })
    }
}
