package server.messaging

/**
 * Encapsulate objects and data needed by handlers to handle message.
 *
 * @property playerId The player in-game unique identifier.
 * @property message Representation of decoded socket message.
 */
interface HandlerContext {
    val playerId: String
    val message: SocketMessage<*>

    /**
     * Send the client [raw] (non-serialized) bytes.
     *
     * If needed, caller must serialize bytes manually. This can be done
     * by calling the appropriate serializer utility.
     */
    suspend fun sendRaw(raw: ByteArray, logOutput: Boolean = true, logFull: Boolean = false)
}
