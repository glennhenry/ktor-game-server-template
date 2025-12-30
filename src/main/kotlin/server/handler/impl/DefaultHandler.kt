package server.handler.impl

import server.handler.SocketMessageHandler
import server.handler.HandlerContext
import server.messaging.SocketMessage
import utils.logging.Logger
import server.messaging.codec.DefaultCodec
import server.messaging.format.DefaultMessage

/**
 * Default handler as the fallback for any unregistered socket handlers.
 *
 * This handler works together with [DefaultCodec] and [DefaultMessage].
 */
class DefaultHandler : SocketMessageHandler<String> {
    override val name: String = "DefaultHandler"
    override val messageType: String = "Default"

    override fun shouldHandle(message: SocketMessage<*>): Boolean {
        return true
    }

    override suspend fun handle(ctx: HandlerContext<String>) = with(ctx) {
        Logger.warn { "No handler registered/implemented for type=${message.type()}" }
    }
}
