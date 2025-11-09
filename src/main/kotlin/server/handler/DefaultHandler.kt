package server.handler

import server.messaging.HandlerContext
import server.messaging.SocketMessage
import server.messaging.SocketMessageHandler
import utils.logging.Logger

/**
 * Default handler as the fallback for any unregistered socket handlers.
 */
class DefaultHandler : SocketMessageHandler {
    override val name: String = "DefaultHandler"

    override fun <T> match(message: SocketMessage<T>): Boolean {
        return true
    }

    override suspend fun handle(ctx: HandlerContext) = with(ctx) {
        Logger.warn {
            "Handler of type=${message.type()} is either unregistered (register it on GameServer.kt) or unimplemented"
        }

    }
}
