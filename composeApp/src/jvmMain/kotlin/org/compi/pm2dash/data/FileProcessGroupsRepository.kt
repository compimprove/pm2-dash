package org.compi.pm2dash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import org.compi.pm2dash.model.CustomProcessGroup
import org.compi.pm2dash.state.ProcessGroupsRepository
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class FileProcessGroupsRepository(
    private val configFile: Path = Paths.get(System.getProperty("user.home"), ".pm2-dash", "groups.json"),
) : ProcessGroupsRepository {
    override suspend fun loadGroups(): Result<List<CustomProcessGroup>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!configFile.exists()) {
                return@runCatching emptyList()
            }

            val root = groupsJson.parseToJsonElement(configFile.readText()) as? JsonObject
                ?: return@runCatching emptyList()
            val groups = root["groups"] as? JsonArray ?: JsonArray(emptyList())

            groups.mapNotNull { element ->
                val group = element as? JsonObject ?: return@mapNotNull null
                CustomProcessGroup(
                    name = group["name"].asStringOrEmpty(),
                    processNames = (group["processNames"] as? JsonArray)
                        ?.mapNotNull { item -> (item as? JsonPrimitive)?.contentOrNull }
                        .orEmpty(),
                )
            }
        }
    }

    override suspend fun saveGroups(groups: List<CustomProcessGroup>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            configFile.parent?.createDirectories()
            val payload = buildJsonObject {
                put("version", JsonPrimitive(1))
                put("groups", buildJsonArray {
                    groups.forEach { group ->
                        add(
                            buildJsonObject {
                                put("name", JsonPrimitive(group.name))
                                put("processNames", buildJsonArray {
                                    group.processNames.forEach { processName ->
                                        add(JsonPrimitive(processName))
                                    }
                                })
                            },
                        )
                    }
                })
            }
            configFile.writeText(groupsJson.encodeToString(JsonObject.serializer(), payload))
        }
    }
}

fun createProcessGroupsRepository(): ProcessGroupsRepository = FileProcessGroupsRepository()

private val groupsJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

private fun kotlinx.serialization.json.JsonElement?.asStringOrEmpty(): String {
    return (this as? JsonPrimitive)?.contentOrNull.orEmpty()
}
