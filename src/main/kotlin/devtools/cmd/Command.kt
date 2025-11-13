package devtools.cmd

import context.ServerContext
import kotlinx.serialization.KSerializer

/**
 * Represents a server command that can be invoked to perform a specific action in server.
 *
 * Implementations can extend the base class [BaseCommand].
 * More specifically, they must correctly define [argInfo], providing an
 * [ArgumentInfo] entry for each field in the command’s argument type [T].
 * The field names in [argInfo] must match the property names of [T].
 *
 * See `test.devtools.CommandDispatcherTest` for example.
 *
 * @property name A human-readable name for the command. Must be unique.
 * @property description A brief explanation of what the command does.
 * @property completionMessage A message displayed after the command completes successfully.
 * @property serializer The serializer that defines how to encode or decode the argument type [T].
 * @property argInfo Metadata describing each argument field of [T].
 * @param T The data class type defining the structure of the command’s arguments.
 */
interface Command<T> {
    val name: String
    val description: String
    val completionMessage: String
    val serializer: KSerializer<T>
    val argInfo: Map<String, ArgumentInfo>

    /**
     * Execution logic of the command.
     *
     * @param serverContext The server's state.
     * @param arg The fully deserialized and validated argument object.
     */
    suspend fun execute(serverContext: ServerContext, arg: T)
}
