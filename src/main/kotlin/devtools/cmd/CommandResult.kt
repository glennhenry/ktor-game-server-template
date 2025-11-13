package devtools.cmd

/**
 * Represents the outcome of executing a server command.
 */
sealed class CommandResult(val message: String = "", val throwable: Throwable? = null) {
    /**
     * The command executed successfully.
     */
    object Executed : CommandResult()

    /**
     * The command failed to execute due to invalid arguments.
     *
     * This includes incorrect types, insufficient arguments, or any illegal input in the command.
     */
    class InvalidArgument(message: String) : CommandResult(message)

    /**
     * The command failed to execute because it was not found (not registered).
     */
    class CommandNotFound(message: String) : CommandResult(message)

    /**
     * An unexpected error occurred during command execution.
     */
    class Error(message: String, throwable: Throwable) : CommandResult(message, throwable)
}
