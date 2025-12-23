package server.protocol.codec

import server.protocol.SocketCodec
import utils.functions.safeAsciiString

/**
 * The default and example implementation of message serializer/deserializer.
 *
 * The logic is merely [String] and [ByteArray] UTF-8 conversion.
 * This is used as the fallback for any kind of message.
 */
object DefaultSerializer {
    fun serialize(input: String): ByteArray {
        return input.toByteArray()
    }

    fun deserialize(output: ByteArray): String {
        return output.safeAsciiString()
    }
}

/**
 * The default and example implementation of [server.protocol.SocketCodec].
 *
 * It delegates codec work into [DefaultSerializer], where actual serializer logic
 * is implemented.
 */
class DefaultCodec : SocketCodec<String> {
    override val name: String = "DefaultCodec"

    override fun verify(data: ByteArray): Boolean {
        return true
    }

    override fun tryEncode(input: String): ByteArray {
        return DefaultSerializer.serialize(input)
    }

    override fun tryDecode(output: ByteArray): String {
        return DefaultSerializer.deserialize(output)
    }
}
