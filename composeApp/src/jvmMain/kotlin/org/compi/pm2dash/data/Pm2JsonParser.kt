package org.compi.pm2dash.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.compi.pm2dash.model.Pm2ProcessDetails
import org.compi.pm2dash.model.Pm2ProcessStatus
import org.compi.pm2dash.model.Pm2ProcessSummary

internal val pm2Json = Json {
    ignoreUnknownKeys = true
}

internal fun parseProcessList(rawJson: String): List<Pm2ProcessDetails> {
    if (rawJson.isBlank()) return emptyList()

    val root = pm2Json.decodeFromString<JsonElement>(rawJson)
    val processes = root as? JsonArray ?: return emptyList()

    return processes.mapNotNull { process ->
        val objectNode = process as? JsonObject ?: return@mapNotNull null
        parseProcess(objectNode)
    }
}

private fun parseProcess(process: JsonObject): Pm2ProcessDetails? {
    val pmId = process["pm_id"].asIntOrNull() ?: return null
    val name = process["name"].asStringOrNull() ?: "unknown-$pmId"
    val pm2Env = process["pm2_env"].asObjectOrEmpty()
    val monit = process["monit"].asObjectOrEmpty()

    val summary = Pm2ProcessSummary(
        pmId = pmId,
        name = name,
        status = parseStatus(pm2Env["status"].asStringOrNull()),
        cpuPercent = monit["cpu"].asDoubleOrNull() ?: 0.0,
        memoryBytes = monit["memory"].asLongOrNull() ?: 0L,
        pid = process["pid"].asIntOrNull(),
        restartCount = pm2Env["restart_time"].asIntOrNull() ?: 0,
        uptimeStartedAtEpochMs = pm2Env["pm_uptime"].asLongOrNull(),
    )

    return Pm2ProcessDetails(
        summary = summary,
        namespace = pm2Env["namespace"].asStringOrNull(),
        interpreter = pm2Env["exec_interpreter"].asStringOrNull(),
        scriptPath = pm2Env["pm_exec_path"].asStringOrNull(),
        workingDirectory = pm2Env["pm_cwd"].asStringOrNull(),
        watchEnabled = pm2Env["watch"].asBooleanOrFalse(),
        instanceCount = pm2Env["instances"].asIntOrNull(),
    )
}

private fun parseStatus(rawStatus: String?): Pm2ProcessStatus = when (rawStatus?.lowercase()) {
    "online" -> Pm2ProcessStatus.Online
    "stopped" -> Pm2ProcessStatus.Stopped
    "errored" -> Pm2ProcessStatus.Errored
    "launching", "waiting restart", "one-launch-status" -> Pm2ProcessStatus.Launching
    else -> Pm2ProcessStatus.Unknown
}

private fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.asIntOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull

private fun JsonElement?.asLongOrNull(): Long? = (this as? JsonPrimitive)?.longOrNull

private fun JsonElement?.asDoubleOrNull(): Double? = (this as? JsonPrimitive)?.doubleOrNull

private fun JsonElement?.asBooleanOrFalse(): Boolean {
    val primitive = this as? JsonPrimitive ?: return false
    return primitive.content.lowercase() == "true"
}

private fun JsonElement?.asObjectOrEmpty(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())
