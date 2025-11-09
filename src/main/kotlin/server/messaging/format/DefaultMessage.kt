package server.messaging.format

import server.messaging.SocketMessage

/**
 * Default implementation of [server.messaging.SocketMessage] where any message format is decoded into UTF-8 [String].
 *
 * This is used as fallback for any unknown message format.
 *
 * - Payload is simply a `String` which is the UTF-8 decoded of raw bytes.
 * - The type of message is always `"[Undetermined]"`.
 * - Any message is always considered as valid.
 * - Message is empty when the string payload is also empty.
 */
class DefaultMessage(override val payload: String) : SocketMessage<String> {
    private var type: String = "[Undetermined]"
    override fun type(): String = type
    override fun isValid(): Boolean = true
    override fun isEmpty(): Boolean = payload.isEmpty()
    override fun toString(): String = payload
}