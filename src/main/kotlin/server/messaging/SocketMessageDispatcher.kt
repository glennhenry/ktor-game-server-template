package server.messaging

import server.handler.DefaultHandler
import utils.logging.Logger
import utils.logging.Logger.LOG_INDENT_PREFIX

/**
 * Dispatch [SocketMessage] to the registered handlers.
 */
class SocketMessageDispatcher() {
    private val handlers = mutableListOf<SocketMessageHandler>()

    fun register(handler: SocketMessageHandler) {
        handlers.add(handler)
    }

    /**
     * Find handlers capable of handling the particular [SocketMessage].
     *
     * @return Will return single list containing [DefaultHandler] if there is no capable handlers.
     */
    fun <T> findHandlerFor(msg: SocketMessage<T>): List<SocketMessageHandler> {
        val default = handlers.first { it.name == "DefaultHandler" }
        val matched = handlers.filter { it.name != "DefaultHandler" && it.match(msg) }

        return (matched.ifEmpty { listOf(default) }).also { selected ->
            Logger.debug {
                buildString {
                    appendLine("[SOCKET DISPATCH]")
                    appendLine("$LOG_INDENT_PREFIX handlers  :")
                    selected.forEach {
                        appendLine("$LOG_INDENT_PREFIX   - ${it.name}")
                    }
                    append("$LOG_INDENT_PREFIX message   : $msg")
                }
            }
        }
    }

    fun shutdown() {
        handlers.clear()
    }
}
