package server.protocol

import server.messaging.SocketMessage

/**
 * Defines a codec responsible for translating messages between raw [ByteArray] data
 * and deserialized objects of type [T].
 *
 * In most cases, implementations of [SocketCodec] do **not** directly perform
 * serialization or deserialization. Instead, they delegate this work to a
 * global singleton or utility object (e.g., `DefaultSerializer`).
 *
 * This design allows other parts of the server (such as handlers, tests, or utilities)
 * to perform serialization independently, without needing to instantiate codec objects
 * or access them through a dispatcher. It also simplifies testing by isolating
 * serialization logic in one place.
 *
 * Conceptually, [SocketCodec] acts as the bridge that wires raw socket
 * bytes into higher-level [SocketMessage] objects for handlers to process.
 */
interface SocketCodec<T> {
    /**
     * Human-readable identifier, typically for debugging or logging purpose.
     */
    val name: String

    /**
     * Check whether this codec can handle the message.
     *
     * Implementation of this should be 'cheap' to use. It should only check
     * the minimum necessary data before the real decoding.
     *
     * For example, bytes codec that verify by detecting magic header,
     * JSON codec that checks for '{', or XML codec that checks for '<'.
     *
     * It shouldn't be common to encounter multiple codecs that verified successfully,
     * unless the format has very similar structure and only differ in the middle.
     *
     * @return Should return `true` only if the data matches the codec’s expected format or signature.
     */
    fun verify(data: ByteArray): Boolean

    /**
     * Encode structured data of type [T] into the raw bytes.
     *
     * @return `null` if unexpected format mismatch happen amidst encoding.
     */
    fun tryEncode(input: T): ByteArray?

    /**
     * Decode raw bytes into structured data of type [T].
     *
     * @return `null` if unexpected format mismatch happen amidst decoding.
     */
    fun tryDecode(output: ByteArray): T?
}
