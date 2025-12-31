package server.handler

import annotation.RevisitLater
import server.messaging.SocketMessage
import server.messaging.codec.SocketCodec
import server.messaging.format.MessageFormat

/**
 * Template for a socket message handler.
 *
 * Each handler is expected to:
 * - Declare the message type it handles via [messageType]
 * - Specify the expected payload type through its generic parameter `T`.
 *   The payload type typically corresponds to the [SocketMessage] implementation
 *   and aligns with the associated [SocketCodec] and [MessageFormat].
 *
 * Incoming [SocketMessage] instances are routed to handlers by the dispatcher
 * based on the message's [SocketMessage.type]. Handlers receive a
 * [HandlerContext]`<T>` whose payload type matches the handler's expectation.
 *
 * Handler matching behavior:
 * - The default [shouldHandle] implementation routes messages by comparing
 *   the message's type with [messageType].
 * - Override [shouldHandle] only if type-based matching is insufficient.
 *
 * **Important:** The generic type parameter `T` is **advisory only**.
 * It represents the expected payload type, but the dispatcher does **not**
 * enforce it at runtime. This means a handler declaring `T = List<Any>`
 * could actually receive a payload of type `Map<String, Any>` if the
 * underlying [SocketMessage] produced that structure. This can happen
 * because handler dispatch relies solely on the message type, not on
 * the generic payload type.
 *
 * @param T The type of payload this handler expects.
 */
@RevisitLater("We may want to enforce type safety on T, so socket dispatchment" +
        "rely on shouldHandle() and runtime validation of declared payload type" +
        "and actual received payload type")
interface SocketMessageHandler<T> {
    /**
     * Human-readable name for the handler, mainly used for logging and debugging.
     */
    val name: String

    /**
     * Message type or identifier that this handler is responsible for.
     */
    val messageType: String

    /**
     * Determines whether this handler should process the given [message].
     *
     * Default implementation compares the message's type with [messageType].
     *
     * @param message The socket message to evaluate.
     * @return `true` if this handler should handle the message; otherwise `false`.
     */
    fun shouldHandle(message: SocketMessage<*>): Boolean {
        return message.type() == messageType
    }

    /**
     * Handles the socket message.
     *
     * @param ctx The handler context, containing the message, player ID,
     * and [HandlerContext.sendRaw] method for sending responses.
     */
    suspend fun handle(ctx: HandlerContext<T>)
}
