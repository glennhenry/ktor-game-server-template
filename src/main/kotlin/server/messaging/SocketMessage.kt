package server.messaging

import server.protocol.MessageFormat
import server.protocol.SocketCodec

/**
 * Represents a decoded (non-raw) socket message whose raw bytes have already been
 * deserialized into a structured [payload], ready to be processed by message handlers.
 *
 * Implementation simply provides a typed container for the decoded payload.
 * The structure of [payload] are defined by the [MessageFormat]
 * and its corresponding [SocketCodec] implementation.
 *
 * For example:
 * - A delimited text message might produce `SocketMessage<List<String>>`
 * - A JSON message might produce `SocketMessage<Map<String, Any?>>`
 *
 * Implementations may also provide additional context, such as a message type
 * identifier returned by [type].
 *
 * @param T The type of the decoded payload, as determined by the message format.
 */
interface SocketMessage<T> {
    /**
     * The deserialized payload extracted from the raw socket data.
     */
    val payload: T

    /**
     * Returns the logical type or identifier of this socket message.
     */
    fun type(): String

    /**
     * Indicates whether the message is valid and safe to process.
     */
    fun isValid(): Boolean

    /**
     * Indicates whether the message is considered as empty.
     */
    fun isEmpty(): Boolean

    /**
     * Returns a human-readable string representation of this message,
     * primarily intended for debugging and logging purposes.
     */
    override fun toString(): String
}
