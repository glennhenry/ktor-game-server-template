package server.messaging

import server.handler.impl.DefaultHandler
import server.handler.SocketMessageHandler
import utils.logging.Logger
import utils.logging.Logger.LOG_INDENT_PREFIX

/**
 * Dispatch [SocketMessage] to the registered handlers.
 */
class SocketMessageDispatcher {
    private val handlers = mutableListOf<SocketMessageHandler<*>>()
    private val handlersByType = mutableMapOf<String, MutableList<SocketMessageHandler<*>>>()

    /**
     * Register a handler for a specific socket message type.
     */
    fun <T> register(handler: SocketMessageHandler<T>) {
        handlers.add(handler)
        handlersByType
            .getOrPut(handler.messageType) { mutableListOf() }
            .add(handler)
    }

    /**
     * Find handlers capable of handling the particular [SocketMessage].
     *
     * @return Will return single list containing [DefaultHandler] if there is no capable handlers.
     */
    fun <T> findHandlerFor(msg: SocketMessage<T>): List<SocketMessageHandler<Any>> {
        val default = handlers.first { it.name == "DefaultHandler" }
        val type = msg.type()

        val byType = handlersByType[type]
        val selected = when {
            !byType.isNullOrEmpty() -> byType         // find by registered type (quick match)
            else -> handlers.filter { it.shouldHandle(msg) } // find by match method (slower)
        }.ifEmpty { listOf(default) }                 // default handler fallback

        logDispatchment(msg, selected)

        @Suppress("UNCHECKED_CAST")
        return selected as List<SocketMessageHandler<Any>>
    }

    private fun <T> logDispatchment(msg: SocketMessage<T>, selected: List<SocketMessageHandler<*>>) {
        Logger.debug {
            buildString {
                appendLine("[SOCKET DISPATCH]")
                appendLine("$LOG_INDENT_PREFIX msg (str) : $msg")
                appendLine("$LOG_INDENT_PREFIX handlers  :")
                selected.forEachIndexed { index, handler ->
                    if (index == selected.lastIndex) {
                        append("$LOG_INDENT_PREFIX   - ${handler.name}")
                    } else {
                        appendLine("$LOG_INDENT_PREFIX   - ${handler.name}")
                    }
                }
            }
        }
    }

    fun shutdown() {
        handlers.clear()
    }
}
