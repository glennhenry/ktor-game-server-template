package server.handler

import server.core.network.Connection
import server.messaging.SocketMessage

/**
 * Default handler context where send implementation is based on [Connection] object.
 */
class DefaultHandlerContext<T : SocketMessage>(
    private val connection: Connection,
    override val playerId: String,
    override val message: T
) : HandlerContext<T> {
    override suspend fun sendRaw(raw: ByteArray, logOutput: Boolean, logFull: Boolean) {
        connection.write(raw, logOutput, logFull)
    }
}
