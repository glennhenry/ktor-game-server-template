package server.messaging

import server.handler.DefaultHandler
import utils.logging.Logger

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
                "Message type=${msg.type()} handled by: ${selected.joinToString { it.name }} | message=$msg"
            }
        }
    }

    fun shutdown() {
        handlers.clear()
    }
}
