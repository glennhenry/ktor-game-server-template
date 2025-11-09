package server.protocol

import server.messaging.SocketMessage

/**
 * Represent the format of socket message with the type [T].
 *
 * @param codec The [SocketCodec] responsible for verifying, decoding, and encoding this message type.
 * @param messageFactory The factory to instantiate a [server.messaging.SocketMessage] instance of type [T].
 * @param T The message data type handled by this instance.
 */
data class MessageFormat<T>(
    val codec: SocketCodec<T>,
    val messageFactory: (T) -> SocketMessage<T>
)
