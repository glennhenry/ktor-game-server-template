package devtools.cmd

import context.ServerContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import utils.JSON
import utils.logging.Logger

/**
 * Dispatch and execute server registered commands.
 *
 * Server commands offers the ability to control and monitor the server.
 * It enables user to modify server's behavior, such as modifying player's data.
 *
 * The server accepts command from the API server, which is typically
 * operated from the external web devtools.
 *
 * How to use:
 * - Implement [Command].
 * - Register the command with [register].
 * - Via the devtools, select command to be executed and input arguments.
 *
 * See example in `test.devtools.CommandDispatcherTest`.
 */
class CommandDispatcher(private val serverContext: ServerContext) {
    private val commands = mutableMapOf<String, Command<*>>()

    /**
     * Register a server command.
     *
     * @param command The command to be registered.
     * @param T The typed argument for the command.
     *
     * @throws IllegalArgumentException If the same command name has already been registered.
     */
    fun <T> register(command: Command<T>) {
        when {
            commands.containsKey(command.name) -> {
                throw IllegalArgumentException(
                    "Duplicate command registration for '${command.name}'; Each command's name must be unique."
                )
            }
        }

        commands[command.name] = command
    }

    /**
     * Handle command request.
     *
     * It involves doing a lookup to the registered commands, deserializing raw
     * arguments input in JSON to the associated typed command argument, then
     * calling `execute` method in the command implementation.
     *
     * @return [CommandResult] that represents the outcome.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun handleCommand(request: CommandRequest): CommandResult {
        val cmd = (commands[request.name]
            ?: return CommandResult.CommandNotFound("Failed to execute command '${request.name}': Command is unknown."))
                as Command<Any?>

        val argsJson = JsonObject(request.args)
        val argsObj = JSON.json.decodeFromJsonElement(cmd.serializer, argsJson)

        Logger.info { "Received command '${cmd.name}' with args=$argsObj" }

        try {
            cmd.execute(serverContext, argsObj)
            Logger.info { "Finished executing command '${cmd.name}'" }
        } catch (e: Exception) {
            Logger.error { "Error while executing command '${cmd.name}': $e" }
            return CommandResult.Error("Error while executing command '${cmd.name}'", e)
        }

        return CommandResult.Executed
    }
}
