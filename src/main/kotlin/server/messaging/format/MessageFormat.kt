package server.messaging.format

import server.messaging.SocketMessage
import server.messaging.codec.SocketCodec

/**
 * Describes a socket message format, separating wire-level data from the
 * higher-level message payload.
 *
 * It acts as a bridge between codec's output from raw bytes
 * with the [messageFactory] which transform it into high-level [SocketMessage].
 *
 * @param Raw The raw message data type produced by the codec (wire representation).
 * @param Payload The semantic payload type exposed to handlers; this may differ
 *        from [Raw].
 * @param codec The [SocketCodec] responsible for verifying, decoding, and encoding
 *        the raw message data.
 * @param messageFactory A factory that transforms the decoded [Raw] data into a
 *        [server.messaging.SocketMessage] carrying a [Payload].
 */
data class MessageFormat<Raw, Payload>(
    val codec: SocketCodec<Raw>,
    val messageFactory: (Raw) -> SocketMessage<Payload>
)
