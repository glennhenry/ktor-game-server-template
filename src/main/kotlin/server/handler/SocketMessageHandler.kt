package server.handler

import server.messaging.SocketMessage

/**
 * A template for socket message handler.
 *
 * Each handler is expected to:
 * - Declare the message type it handles via [SocketMessageHandler.messageType]
 * - Declare the expected payload type via its generic parameter `T`
 *
 * The dispatcher will route incoming [SocketMessage] instances to handlers
 * based on the message's [SocketMessage.type], and will provide a
 * [HandlerContext]`<T>` whose payload type matches the handler's expectation.
 *
 * Handler matching behavior:
 * - The default [shouldHandle] implementation routes messages based on their
 *   [SocketMessage.type].
 * - Handlers should override [shouldHandle] only when type-based matching is insufficient.
 */
interface SocketMessageHandler<T> {
    val name: String

    /**
     * Message type or identifier the handler is supposed to handle.
     */
    val messageType: String

    /**
     * Whether the handler should handle this socket message.
     *
     * Default implementation matches defined [messageType]
     * with the type of received socket message.
     */
    fun shouldHandle(message: SocketMessage<*>): Boolean {
        return message.type() == messageType
    }

    /**
     * Handle the socket message.
     *
     * @param ctx Context of handler, included with the socket message
     * and [HandlerContext.sendRaw] method.
     */
    suspend fun handle(ctx: HandlerContext<T>)
}
