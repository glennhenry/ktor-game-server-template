package example

import com.mongodb.assertions.Assertions.assertFalse
import server.protocol.SocketCodec
import kotlin.test.*

/**
 * Demonstrate an example to test codec and message format.
 */
class ExampleCodecTest {
    @Test
    fun `test verify codec success`() {
        val codec = ExampleCodec()
        assertTrue(codec.verify(byteArrayOf(3.toByte())))
    }

    @Test
    fun `test verify codec fail`() {
        val codec = ExampleCodec()
        assertFalse(codec.verify(byteArrayOf(14.toByte())))
    }

    @Test
    fun `test decode verify success but invalid format (even-length)`() {
        val bytes = byteArrayOf(3.toByte(), 3, 4, 4)
        assertNull(ExampleSerializer.deserialize(bytes))
    }

    @Test
    fun `test decode success`() {
        val bytes = byteArrayOf(8.toByte(), 3, 4, 4, 5, 9, 127, 111, 100)
        assertEquals(listOf("8", "3-4", "4-5", "9-127", "111-100"), ExampleSerializer.deserialize(bytes))
    }

    @Test
    fun `test encode fail invalid string (1 byte in a group)`() {
        val list = listOf("7", "34", "4-5", "9-127", "111-100")
        assertNull(ExampleSerializer.serialize(list))
    }

    @Test
    fun `test encode fail invalid string (3 byte in a group)`() {
        val list = listOf("9", "34-43-22", "4-5", "9-127", "111-100")
        assertNull(ExampleSerializer.serialize(list))
    }

    @Test
    fun `test encode fail one of the number is not a byte`() {
        val list = listOf("8", "343-1", "4-5", "9-127", "111-100")
        assertNull(ExampleSerializer.serialize(list))
    }

    @Test
    fun `test encode success`() {
        val list = listOf("8", "3-4", "4-5", "9-127", "111-100")
        assertContentEquals(byteArrayOf(8, 3, 4, 4, 5, 9, 127, 111, 100), ExampleSerializer.serialize(list))
    }
}

/**
 * Example of how to implement serializer/deserializer and SocketCodec implementation.
 *
 * - The format operates on a list of string.
 * - Valid raw format is a byte array that is prefixed by some 1-digit header number.
 *   which is the size of message % 9
 * - After that number, everything is byte number (-128 to 127).
 * - The byte array has even length (not including the header) and each number is paired.
 *
 * Example:
 * - **decoded**: `[3 124-94 9-23 51-32]` (string representation)
 * - **encoded**: `[3 124 94 9 23 51 32]` (assume byte representation)
 *
 * **Decoding**:
 * - Drops the header number.
 * - Ensure the size is even. **FAIL: return null**.
 * - For each byte group them into two, then ensure all bytes are valid
 *   (number between -127 and 128). **FAIL: return null**.
 * - Turn each byte group into string like `<b1>-<b2>`.
 *
 * **Encoding**:
 * - Create header number which is the size of input * 2 % 9.
 * - Create byte array, include the header number first.
 * - For each string, split with '-', then ensure each number
 *   is a valid byte. **FAIL: return null**.
 * - Add all bytes to the byte array.
 */
object ExampleSerializer {
    fun serialize(input: List<String>): ByteArray? {
        val header = ((input.size - 1) * 2) % 9
        val result = mutableListOf(header.toByte())

        input.drop(1).filter {
            val group = it.split("-")
            if (group.size != 2) return null

            val b1 = group[0]
            val b2 = group[1]

            if (b1.toIntOrNull() == null) return null
            if (b2.toIntOrNull() == null) return null

            if (b1.toInt() !in -127..128) return null
            if (b2.toInt() !in -127..128) return null

            true
        }.forEach {
            val group = it.split("-")
            result.add(group[0].toByte())
            result.add(group[1].toByte())
        }

        return result.toByteArray()
    }

    fun deserialize(output: ByteArray): List<String>? {
        val header = output.first()
        if (header.toInt() !in -127..128) return null
        val withoutHeader = output.drop(1)
        if (withoutHeader.size % 2 != 0) return null

        val result = mutableListOf(header.toString())

        val iterator = withoutHeader.iterator()
        while (iterator.hasNext()) {
            val b1 = iterator.next()
            if (!iterator.hasNext()) return null
            val b2 = iterator.next()
            result.add("${b1}-${b2}")
        }

        return result
    }
}

/**
 * Example implementation of SocketCodec where most work is delegated to ExampleSerializer.
 *
 * Only [verify] is the real implementation here.
 */
class ExampleCodec : SocketCodec<List<String>> {
    override val name: String = "ExampleCodec"

    override fun verify(data: ByteArray): Boolean {
        return data.first().toInt() in 0..9
    }

    override fun tryEncode(input: List<String>): ByteArray? {
        return ExampleSerializer.serialize(input)
    }

    override fun tryDecode(output: ByteArray): List<String>? {
        return ExampleSerializer.deserialize(output)
    }
}
