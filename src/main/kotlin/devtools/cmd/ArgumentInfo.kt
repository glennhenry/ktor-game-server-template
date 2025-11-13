package devtools.cmd

/**
 * Represents metadata describing a single argument accepted by a server [Command].
 *
 * ### Example
 * Given an argument type:
 * ```kotlin
 * @Serializable
 * data class GiveArgs(
 *     val playerId: String
 * )
 * ```
 *
 * The corresponding `GiveCommand` should declare its argument information as:
 * ```kotlin
 * val argInfo = mapOf(
 *     // "The field named 'playerId' has argument info of..."
 *     "playerId" to ArgumentInfo(
 *         name = "playerId",
 *         description = "The unique identifier of the target player.",
 *         required = true,
 *         type = CommandType.STRING
 *     )
 * )
 * ```
 *
 * ### Notes
 * - The [name] must match the property name in the argument class.
 * - Optional arguments must specify a [defaultValue].
 *
 * @property name The name of the argument, corresponding to a field in the argument class.
 * @property description A human-readable explanation of what this argument controls or affects.
 * @property required Whether this argument must be explicitly provided by the user.
 * @property defaultValue The default value if the argument is optional.
 *                        This will be ignored if the argument is required.
 * @property type The data type of the argument.
 */
data class ArgumentInfo(
    val name: String,
    val description: String,
    val required: Boolean,
    val defaultValue: String? = null,
    val type: CommandType
)

enum class CommandType {
    String, Int, Double, Boolean
}
