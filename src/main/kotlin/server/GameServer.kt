package server

import context.ServerContext
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import server.core.network.Connection
import server.core.network.DefaultConnection
import server.core.Server
import server.handler.DefaultHandler
import server.messaging.DefaultHandlerContext
import server.messaging.format.DefaultMessage
import server.messaging.SocketMessage
import server.messaging.SocketMessageDispatcher
import server.protocol.codec.DefaultCodec
import server.protocol.MessageFormat
import server.tasks.TaskName
import utils.logging.Logger
import java.net.SocketException
import kotlin.system.measureTimeMillis

data class GameServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 7777,
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
            Logger.warn("Game server is already running")
            return
        }
        running = true

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

                    var msgType = "[Undetermined]"
                    val elapsed = measureTimeMillis {
                        msgType = handleMessage(connection, data)
                    }

                    Logger.debug {
                        buildString {
                            appendLine("<===== [SOCKET END] of type $msgType handled for playerId=${connection.playerId} in ${elapsed}ms")
                            append("====================================================================================================")
                        }
                    }
                }
            } catch (_: ClosedByteChannelException) {
                Logger.info { "Client ${connection.remoteAddress} disconnected abruptly (connection reset)" }
            } catch (e: SocketException) {
                when {
                    e.message?.contains("Connection reset") == true -> {
                        Logger.info { "Client ${connection.remoteAddress} connection was reset by peer" }
                    }

                    e.message?.contains("Broken pipe") == true -> {
                        Logger.info { "Client ${connection.remoteAddress} connection broken (broken pipe)" }
                    }

                    else -> {
                        Logger.warn { "Socket exception for ${connection.remoteAddress}: ${e.message}" }
                    }
                }
            } catch (e: Exception) {
                Logger.error { "Unexpected error in socket for ${connection.remoteAddress}: $e" }
                e.printStackTrace()
            } finally {
                Logger.info { "Cleaning up connection for ${connection.remoteAddress}" }

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
     * Handle message from [Connection] with bytes [data] by:
     *
     * 1. Infer message format.
     * 2. Find codecs for it.
     * 3. Construct high-level [SocketMessage].
     * 4. Find socket handlers for it.
     *
     * ```
     * raw bytes -> codec.tryDecode() -> payload (T)
     * payload -> messageFactory(payload) -> SocketMessage<T>
     * SocketMessage -> HandlerContext
     * ```
     *
     * It is possible that multiple codec decoded the message. If such thing happened,
     * the first codec (by registration order) that successfully decoded it will be used.
     *
     * @return The type of message being handled.
     */
    private suspend fun handleMessage(connection: Connection, data: ByteArray): String {
        if (data.isEmpty()) {
            Logger.debug { "===== [SOCKET] Ignored empty byte array from connection=$connection" }
            return "[Empty data]"
        }

        // codecs that successfully verify the format
        val potentialFormats = serverContext.codecDispatcher.findCodecFor(data)
        // list of msgType to codec that successfully deserialize and transform the message
        val goodCodecs = mutableListOf<Pair<String, MessageFormat<Any>>>()

        for (format in potentialFormats) {
            try {
                @Suppress("UNCHECKED_CAST")
                // for each potential codec, try decoding
                val deserialized = (format as MessageFormat<Any>).codec.tryDecode(data)
                if (deserialized == null) {
                    Logger.debug { "Codec ${format.codec.name} verified successfully but failed to deserialize data, unexpected format mismatch occurred in the middle." }
                    continue
                }

                // if decode success and message is not empty, construct the SocketMessage
                val message = format.messageFactory(deserialized)
                if (message.isEmpty()) {
                    Logger.debug { "===== [SOCKET] Ignored empty message from connection=$connection, raw: $message" }
                    continue
                }

                val msgType = message.type()
                Logger.debug {
                    "=====> [SOCKET START]: of type $msgType, raw: $data for playerId=${connection.playerId}, bytes=${data.size} (decoded by ${format.codec.name})"
                }

                goodCodecs.add(msgType to format)

                // pass the SocketMessage to handlers
                val handlerContext = DefaultHandlerContext(connection, connection.playerId, message)
                socketDispatcher.findHandlerFor(message).forEach { handler ->
                    handler.handle(handlerContext)
                }
            } catch (e: Exception) {
                Logger.error { "Codec ${format.codec.name} error during decoding; e: $e" }
                return "[Decode error]"
            }
        }

        if (goodCodecs.size > 1) {
            Logger.warn {
                buildString {
                    appendLine("Found multiple codec that successfully decoded the same data: ${goodCodecs.joinToString { "${it.second.codec.name}, " }}")
                    appendLine("Parsed msg type (in the same order): ${goodCodecs.joinToString { "${it.first}, " }}")
                }
            }
        }

        return goodCodecs.first().first
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
