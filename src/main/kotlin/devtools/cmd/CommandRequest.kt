package devtools.cmd

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represent a server command request from the external web devtools.
 *
 * To create `CommandRequest` for tests, you can do it like:
 * ```kotlin
 * val args = buildJsonObject {
 *     put("playerId", JsonPrimitive("pid123"))
 *     put("itemId", JsonPrimitive("sword"))
 * }
 * ```
 */
@Serializable
data class CommandRequest(
    val name: String,
    val args: Map<String, JsonElement>
)
