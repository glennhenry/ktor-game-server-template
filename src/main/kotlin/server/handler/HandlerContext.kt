package server.handler

import annotation.RevisitLater
import server.messaging.SocketMessage

/**
 * Encapsulate objects and data needed by handlers to handle message.
 *
 * **Important:** The generic type parameter `T` is **advisory only**.
 * It represents the expected payload type, but the dispatcher does **not**
 * enforce it at runtime. This means a handler declaring `T = List<Any>`
 * could actually receive a payload of type `Map<String, Any>` if the
 * underlying [SocketMessage] produced that structure. This can happen
 * because handler dispatch relies solely on the message type, not on
 * the generic payload type.
 *
 * @property playerId The player in-game unique identifier.
 * @property message Representation of decoded socket message.
 * @param T Type of [SocketMessage.payload] which the handler operates on.
 */
@RevisitLater("We may want to enforce type safety on T, so socket dispatchment" +
        "rely on shouldHandle() and runtime validation of declared payload type" +
        "and actual received payload type")
interface HandlerContext<T> {
    val playerId: String
    val message: SocketMessage<T>

    /**
     * Send the client [raw] (non-serialized) bytes.
     *
     * If needed, caller must serialize bytes manually. This can be done
     * by calling the appropriate serializer utility.
     */
    suspend fun sendRaw(raw: ByteArray, logOutput: Boolean = true, logFull: Boolean = false)
}
