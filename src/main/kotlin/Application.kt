import api.routes.fileRoutes
import com.mongodb.kotlin.client.coroutine.MongoClient
import context.DefaultContextTracker
import context.ServerContext
import context.ServerServices
import core.data.GameDefinition
import data.MongoImpl
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.bson.Document
import server.GameServer
import server.GameServerConfig
import server.ServerContainer
import server.core.OnlinePlayerRegistry
import server.core.Server
import server.protocol.SocketCodecDispatcher
import server.tasks.ServerTaskDispatcher
import user.PlayerAccountRepositoryMongo
import user.auth.DefaultAuthProvider
import user.auth.SessionManager
import utils.JSON
import utils.logging.Logger
import utils.logging.LoggerSettings
import utils.logging.toInt
import utils.logging.toLogLevel
import java.io.File
import java.text.SimpleDateFormat

fun main(args: Array<String>) = EngineMain.main(args)

@Suppress("unused")
suspend fun Application.module() {
    /* 1. Setup logger */
    Logger.updateSettings { original ->
        LoggerSettings(
            minimumLevel = config().getInt("logger.level", original.minimumLevel.toInt()).toLogLevel(),
            colorfulLog = config().getBoolean("logger.colorfulLog", original.colorfulLog),
            colorizeLevelLabelOnly = config().getBoolean(
                "logger.colorizeLevelLabelOnly",
                original.colorizeLevelLabelOnly
            ),
            useForegroundColor = config().getBoolean("logger.useForegroundColor", original.useForegroundColor),
            fileNamePadding = config().getInt("logger.maximumFileNameLength", original.fileNamePadding),
            maximumLogMessageLength = config().getInt(
                "logger.maximumLogMessageLength",
                original.maximumLogMessageLength
            ),
            maximumLogFileSize = config().getInt("logger.maximumLogFileSize", original.maximumLogFileSize),
            maximumLogFileRotation = config().getInt("logger.maximumLogFileRotation", original.maximumLogFileRotation),
            logDateFormatter = SimpleDateFormat(
                config().getString(
                    "logger.logDateFormatter",
                    original.logDateFormatter.toPattern()
                )
            ),
            fileDateFormatter = SimpleDateFormat(
                config().getString(
                    "logger.fileDateFormatter",
                    original.fileDateFormatter.toPattern()
                )
            )
        )
    }

    /* 2. Setup serialization */
    val module = SerializersModule {}
    val json = Json {
        serializersModule = module
        classDiscriminator = "_t"
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    @OptIn(ExperimentalSerializationApi::class)
    install(ContentNegotiation) {
        json(json)
    }
    JSON.initialize(json)

    /* 3. Install CORS */
    install(CORS) {
        anyHost() // change this on production
        allowHeader(HttpHeaders.ContentType)
        allowHeaders { true }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }

    /* 4. Install call logging */
    install(CallLogging)

    /* 5. Install status pages */
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            Logger.error("Server error: ${cause.message}")
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
        unhandled { call ->
            Logger.error("Unhandled API route: ${call.request.httpMethod} ${call.request.uri}.")
        }
    }

    /* 6. Configure Database */
    val database = startMongo(
        databaseName = "changeme",
        mongoUrl = config().getString("mongo.url", "mongodb://localhost:27017"),
        adminEnabled = config().getBoolean("game.enableAdmin", true)
    )

    /* 7. Setup ServerContext */
    val playerAccountRepository = PlayerAccountRepositoryMongo(database.getCollection("player_account"))
    val sessionManager = SessionManager()
    val authProvider = DefaultAuthProvider(database, playerAccountRepository, sessionManager)
    val onlinePlayerRegistry = OnlinePlayerRegistry()
    val contextTracker = DefaultContextTracker()
    val codecDispatcher = SocketCodecDispatcher()
    val taskDispatcher = ServerTaskDispatcher()
    val services = ServerServices()
    val serverContext = ServerContext(
        db = database,
        playerAccountRepository = playerAccountRepository,
        sessionManager = sessionManager,                   // is not used unless auth is implemented
        authProvider = authProvider,                       // is not used unless auth is implemented
        onlinePlayerRegistry = onlinePlayerRegistry,       // not much used typically
        contextTracker = contextTracker,
        codecDispatcher = codecDispatcher,
        taskDispatcher = taskDispatcher,
        services = services
    )

    /* 8. Initialize GameDefinition */
    GameDefinition.initialize()

    /* 9. Register routes */
    routing {
        fileRoutes()
    }

    /* 10. Initialize servers */
    // build server configs
    val gameServerConfig = GameServerConfig(
        host = config().getString("game.host", "127.0.0.1"),
        port = config().getInt("game.host", 7777)
    )

    val servers = buildList<Server> {
        GameServer(gameServerConfig)
    }

    /* 11. Run all the servers */
    val container = ServerContainer(servers, serverContext)
    run {
        container.initializeAll()
        container.startAll()
    }

    val apiPort = config().getString("ktor.deployment.port", "8080")

    Logger.info { "All server started successfully" }
    Logger.info { "Socket server listening on ${gameServerConfig.host}:${gameServerConfig.port}" }
    Logger.info { "API server available at ${gameServerConfig.host}:$apiPort" }

    if (File("docs/index.html").exists()) {
        Logger.info { "Docs website available on ${gameServerConfig.host}:$apiPort" }
    } else {
        Logger.verbose { "Docs website not available. Optionally, run 'npm install' & 'npm run dev' in the docs folder to preview it." }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            container.shutdownAll()
        }
        Logger.info("Server shutdown complete")
    })
}

fun startMongo(databaseName: String, mongoUrl: String, adminEnabled: Boolean): MongoImpl {
    return runBlocking {
        try {
            val mongoc = MongoClient.create(mongoUrl)
            val db = mongoc.getDatabase("admin")
            val commandResult = db.runCommand(Document("ping", 1))
            Logger.info { "MongoDB connection successful: $commandResult" }
            MongoImpl(mongoc.getDatabase(databaseName), adminEnabled)
        } catch (e: Exception) {
            Logger.error { "MongoDB connection failed inside timeout: ${e.message}" }
            throw e
        }
    }
}

// REPLACE add
data class ServerConfig(
    val adminEnabled: Boolean,
    val mongoUrl: String,
)

fun ApplicationConfig.getString(path: String, default: String): String {
    return this.propertyOrNull(path)?.getString() ?: default
}

fun ApplicationConfig.getInt(path: String, default: Int): Int {
    return this.propertyOrNull(path)?.getString()?.toIntOrNull() ?: default
}

fun ApplicationConfig.getBoolean(path: String, default: Boolean): Boolean {
    return this.propertyOrNull(path)?.getString()?.toBooleanStrict() ?: default
}

/**
 * Use the application.yaml config
 */
fun Application.config(): ApplicationConfig = this.environment.config
