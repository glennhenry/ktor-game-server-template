package server

import SERVER_ADDRESS
import SERVER_SOCKET_PORT
import context.ServerContext
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import server.core.Server
import server.core.network.Connection
import server.core.network.DefaultConnection
import server.handler.impl.DefaultHandler
import server.handler.DefaultHandlerContext
import server.messaging.SocketMessage
import server.messaging.SocketMessageDispatcher
import server.messaging.format.DefaultMessage
import server.messaging.format.MessageFormat
import server.messaging.codec.DefaultCodec
import server.tasks.TaskName
import utils.functions.hexAsciiString
import utils.functions.safeAsciiString
import utils.logging.Logger
import utils.logging.Logger.LOG_INDENT_PREFIX
import kotlin.system.measureTimeMillis

data class GameServerConfig(
    val host: String = SERVER_ADDRESS,
    val port: Int = SERVER_SOCKET_PORT,
)

class GameServer(private val config: GameServerConfig) : Server {
    override val name: String = "GameServer"

    private lateinit var gameServerScope: CoroutineScope
    private lateinit var serverContext: ServerContext
    private val socketDispatcher = SocketMessageDispatcher()

    private var running = false
    override fun isRunning(): Boolean = running

    override suspend fun initialize(scope: CoroutineScope, context: ServerContext) {
        this.gameServerScope = CoroutineScope(scope.coroutineContext + SupervisorJob() + Dispatchers.IO)
        this.serverContext = context

        socketDispatcher.register(DefaultHandler())
        // REPLACE
        context.taskDispatcher.registerTask(
            name = TaskName.DummyName,
            stopFactory = {},
            deriveTaskId = { playerId, name, _ ->
                // RTD-playerId123-unit
                "${name.code}-$playerId-unit"
            }
        )
        // register codec (serializer, deserializer) and message format
        // REPLACE
        val possibleFormats = listOf<MessageFormat<*>>(
            MessageFormat(
                codec = DefaultCodec(),
                messageFactory = { DefaultMessage(it) }
            )
        )
        possibleFormats.forEach {
            context.codecDispatcher.register(it)
        }
    }

    override suspend fun start() {
        if (running) {
            Logger.warn { "Game server is already running" }
            return
        }
        running = true

        Logger.info { "Socket server listening on ${config.host}:${config.port}" }

        gameServerScope.launch {
            try {
                val selectorManager = SelectorManager(Dispatchers.IO)
                val serverSocket = aSocket(selectorManager).tcp().bind(config.host, config.port)

                while (isActive) {
                    val socket = serverSocket.accept()
                    val connection = DefaultConnection(
                        inputChannel = socket.openReadChannel(),
                        outputChannel = socket.openWriteChannel(autoFlush = true),
                        remoteAddress = socket.remoteAddress.toString(),
                        connectionScope = CoroutineScope(gameServerScope.coroutineContext + SupervisorJob() + Dispatchers.Default),
                    )
                    Logger.info { "New client: ${connection.remoteAddress}" }
                    handleClient(connection)
                }
            } catch (e: Exception) {
                Logger.error { "ERROR on server: $e" }
                shutdown()
            }
        }
    }

    /**
     * Handle client [Connection] in suspending manner until data is available.
     */
    private fun handleClient(connection: Connection) {
        connection.connectionScope.launch {
            try {
                loop@ while (isActive) {
                    val (bytesRead, data) = connection.read()
                    if (bytesRead <= 0) break@loop

                    serverContext.onlinePlayerRegistry.updateLastActivity(connection.playerId)

                    // start handle
                    var msgType = listOf("[Undetermined]")
                    val elapsed = measureTimeMillis {
                        msgType = handleMessage(connection, data)
                    }

                    // end handle
                    Logger.debug {
                        buildString {
                            appendLine("<===== [SOCKET END]")
                            appendLine("$LOG_INDENT_PREFIX types     : ${msgType.joinToString(", ")}")
                            appendLine("$LOG_INDENT_PREFIX playerId  : ${connection.playerId}")
                            appendLine("$LOG_INDENT_PREFIX duration  : ${elapsed}ms")
                            append("====================================================================================================")
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.error { "Exception in client socket $connection: $e" }
            } finally {
                Logger.info { "Cleaning up for $connection" }

                // Only perform cleanup if playerId is set (client was authenticated)
                if (connection.playerId != "[Undetermined]") {
                    serverContext.onlinePlayerRegistry.markOffline(connection.playerId)
                    serverContext.playerAccountRepository.updateLastLogin(connection.playerId, getTimeMillis())
                    serverContext.contextTracker.removeContext(connection.playerId)
                    serverContext.taskDispatcher.stopAllTasksForPlayer(connection.playerId)
                }

                connection.shutdown()
            }
        }
    }

    /**
     * Handle message from [Connection] with raw bytes [data] by:
     *
     * 1. Find codecs that can decode it.
     * 2. Construct the high-level [SocketMessage] for the data.
     * 3. Find socket handlers responsible for handling the message.
     *
     * ```
     * data -> detectMessageFormat(data) -> codec.tryDecode(data) -> payload (T)
     * payload (T) -> messageFactory(payload) -> SocketMessage<T>
     * SocketMessage<T> -> findHandlerFor(socketMessage) -> handlers
     * handlers.forEach.handle(msg)
     * ```
     *
     * - It's possible that for the same data, multiple codecs successfully decoded it.
     *   In this case, each codec may construct different `SocketMessage`, and all
     *   different interpretation will be passed to handlers.
     * - This phenomenon is somewhat vague, whether it's intended or not,
     *   so whenever it happened, there will be warning.
     * - Multiple handlers handling the same message is completely normal.
     *
     * @return The various types of message decoded successfully, used merely
     *         to mark the end of socket dispatchment.
     */
    private suspend fun handleMessage(connection: Connection, data: ByteArray): List<String> {
        if (data.isEmpty()) {
            Logger.debug { "===== [SOCKET] Ignored empty byte array from connection=$connection" }
            return listOf("[Empty data]")
        }

        Logger.debug {
            buildString {
                appendLine("=====> [SOCKET RECEIVE]")
                appendLine("$LOG_INDENT_PREFIX playerId  : ${connection.playerId}")
                appendLine("$LOG_INDENT_PREFIX bytes     : ${data.size}")
                appendLine("$LOG_INDENT_PREFIX raw       : ${data.safeAsciiString()}")
                append("$LOG_INDENT_PREFIX raw (hex) : ${data.hexAsciiString()}")
            }
        }

        // identify what format this message is
        val potentialFormats = serverContext.codecDispatcher.detectMessageFormat(data)
        // keep track the right codec that successfully deserialized the message
        val matchedFormats = mutableListOf<Pair<String, MessageFormat<Any>>>()

        for (format in potentialFormats) {
            try {
                @Suppress("UNCHECKED_CAST")
                // for each potential format, tell its codec to try to decode
                val deserialized = (format as MessageFormat<Any>).codec.tryDecode(data)
                if (deserialized == null) {
                    Logger.debug { "Codec ${format.codec.name} verified successfully but failed to deserialize data, unexpected format mismatch occurred in the middle." }
                    continue
                }

                // if decode succeeded and message is not empty, construct the SocketMessage representation
                val message = format.messageFactory(deserialized)
                if (message.isEmpty()) {
                    Logger.debug { "[SOCKET IGNORE] Ignored empty message from connection=$connection, raw: $message" }
                    continue
                }

                // log each decoding result
                val msgType = message.type()
                Logger.debug {
                    buildString {
                        appendLine("[SOCKET DECODE]")
                        appendLine("$LOG_INDENT_PREFIX type      : $msgType")
                        append("$LOG_INDENT_PREFIX codec     : ${format.codec.name}")
                    }
                }

                matchedFormats.add(msgType to format)

                // pass the SocketMessage to handlers
                val handlerContext = DefaultHandlerContext(connection, connection.playerId, message)
                socketDispatcher.findHandlerFor(message).forEach { handler ->
                    handler.handle(handlerContext)
                }
            } catch (e: Exception) {
                Logger.error { "Codec ${format.codec.name} error during decoding; e: $e" }
                return listOf("[Decode error]")
            }
        }

        if (matchedFormats.size > 1) {
            Logger.warn {
                buildString {
                    appendLine("Found multiple codec that successfully decoded the same data: ${matchedFormats.joinToString { "${it.second.codec.name}, " }}")
                    appendLine("Parsed msg type (in the same order): ${matchedFormats.joinToString { "${it.first}, " }}")
                }
            }
        }

        return matchedFormats.map { it.first }
    }

    override suspend fun shutdown() {
        running = false
        serverContext.contextTracker.shutdown()
        serverContext.onlinePlayerRegistry.shutdown()
        serverContext.sessionManager.shutdown()
        serverContext.taskDispatcher.shutdown()
        socketDispatcher.shutdown()
        gameServerScope.cancel()
    }
}
