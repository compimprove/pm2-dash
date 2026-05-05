package org.compi.pm2dash.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.compi.pm2dash.model.Pm2ProcessStatus

class Pm2JsonParserTest {
    @Test
    fun `parseProcessList maps pm2 json into process details`() {
        val raw = """
            [
              {
                "pm_id": 3,
                "name": "user-service",
                "pid": 44013,
                "monit": {
                  "cpu": 12.7,
                  "memory": 73400320
                },
                "pm2_env": {
                  "status": "online",
                  "restart_time": 4,
                  "pm_uptime": 1715000000000,
                  "namespace": "default",
                  "exec_interpreter": "node",
                  "pm_exec_path": "/Users/compi/service/dist/start.js",
                  "pm_cwd": "/Users/compi/service",
                  "watch": false,
                  "instances": 1
                }
              }
            ]
        """.trimIndent()

        val processes = parseProcessList(raw)

        assertEquals(1, processes.size)
        val process = processes.single()
        assertEquals("user-service", process.summary.name)
        assertEquals(Pm2ProcessStatus.Online, process.summary.status)
        assertEquals(44013, process.summary.pid)
        assertEquals(4, process.summary.restartCount)
        assertEquals(73_400_320L, process.summary.memoryBytes)
        assertEquals("/Users/compi/service", process.workingDirectory)
        assertEquals("node", process.interpreter)
        assertEquals(false, process.watchEnabled)
    }

    @Test
    fun `parseProcessList returns empty for blank payload`() {
        assertTrue(parseProcessList("").isEmpty())
    }
}
