package context

import data.Database
import data.EmptyDatabase
import server.core.OnlinePlayerRegistry
import server.protocol.SocketCodecDispatcher
import server.tasks.ServerTaskDispatcher
import user.EmptyPlayerAccountRepository
import user.PlayerAccountRepository
import user.auth.AuthProvider
import user.auth.EmptyAuthProvider
import user.auth.SessionManager

/**
 * Represents the **global server-side context** which includes various server components.
 *
 * @property db [Database] instance of the server.
 * @property playerAccountRepository Repository class that holds player accounts.
 * @property sessionManager Manages session of players.
 * @property authProvider Provides authentication functions.
 * @property onlinePlayerRegistry Keep tracks online status of each player.
 * @property contextTracker Tracks and manages each player's context.
 * @property codecDispatcher Dispatch and track the known message format and registered codecs
 *                           for network messages.
 * @property taskDispatcher Provide API to start and stop server-sided task.
 */
data class ServerContext(
    val db: Database,
    val playerAccountRepository: PlayerAccountRepository,
    val sessionManager: SessionManager,
    val authProvider: AuthProvider,
    val onlinePlayerRegistry: OnlinePlayerRegistry,
    val contextTracker: ContextTracker,
    val codecDispatcher: SocketCodecDispatcher,
    val taskDispatcher: ServerTaskDispatcher
) {
    companion object {
        /**
         * Create a fake, simple to use [ServerContext] for testing purposes.
         *
         * It allows injection of interface-based dependencies such as [Database],
         * [PlayerAccountRepository], and [AuthProvider].
         *
         * By default, the [FakeContextTracker] is used, while all other components
         * (e.g. [SessionManager], [OnlinePlayerRegistry], [SocketCodecDispatcher], and
         * [ServerTaskDispatcher]) are initialized with their default implementations.
         */
        fun fake(
            db: Database = EmptyDatabase(),
            playerAccountRepository: PlayerAccountRepository = EmptyPlayerAccountRepository(),
            authProvider: AuthProvider = EmptyAuthProvider(),
            contextTracker: ContextTracker = FakeContextTracker()
        ): ServerContext {
            return ServerContext(
                db = db,
                playerAccountRepository = playerAccountRepository,
                sessionManager = SessionManager(),
                authProvider = authProvider,
                onlinePlayerRegistry = OnlinePlayerRegistry(),
                contextTracker = contextTracker,
                codecDispatcher = SocketCodecDispatcher(),
                taskDispatcher = ServerTaskDispatcher()
            )
        }
    }
}

/**
 * Retrieve the [PlayerContext] of [playerId].
 *
 * @return `null` if context is not found.
 */
fun ServerContext.getPlayerContextOrNull(playerId: String): PlayerContext? =
    contextTracker.getContext(playerId)

/**
 * Retrieve the non-null [PlayerContext] of [playerId].
 *
 * @throws IllegalStateException if context is not found.
 */
fun ServerContext.requirePlayerContext(playerId: String): PlayerContext =
    getPlayerContextOrNull(playerId)
        ?: error("PlayerContext not found for playerId=$playerId")
