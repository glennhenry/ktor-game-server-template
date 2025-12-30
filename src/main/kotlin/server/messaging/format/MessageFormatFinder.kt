package server.messaging.format

import utils.logging.Logger

/**
 * Track message format and their codecs.
 */
class MessageFormatFinder {
    private val formats = mutableListOf<MessageFormat<*, *>>()

    fun register(format: MessageFormat<*, *>) {
        formats.add(format)
    }

    /**
     * Detect the possible [MessageFormat] for raw bytes [data].
     */
    fun detectMessageFormat(data: ByteArray): List<MessageFormat<*, *>> {
        lateinit var default: MessageFormat<*, *>
        val matched = mutableListOf<MessageFormat<*, *>>()

        for (format in formats) {
            if (format.codec.name == "DefaultCodec") default = format
            try {
                val verifySuccess = format.codec.verify(data)
                if (verifySuccess) {
                    matched.add(format)
                }
            } catch (e: Exception) {
                Logger.verbose {
                    val peek = data.copyOfRange(0, minOf(20, data.size))
                    "${format.codec.name} verify failed; peek=$peek; $e"
                }
            }
        }

        return matched.ifEmpty { listOf(default) }
    }
}
