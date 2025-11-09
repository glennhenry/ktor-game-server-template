package server.messaging

import server.core.network.Connection

/**
 * Default handler context where send implementation is based on [Connection] object.
 */
class DefaultHandlerContext(
    private val connection: Connection,
    override val playerId: String,
    override val message: SocketMessage<*>
) : HandlerContext {
    override suspend fun sendRaw(raw: ByteArray, logOutput: Boolean, logFull: Boolean) {
        connection.write(raw, logOutput, logFull)
    }
}
