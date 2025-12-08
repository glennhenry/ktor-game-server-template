package ws

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

typealias ClientSessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>

/**
 * Track websocket connections.
 */
class WebsocketManager() {
    private val clients = ClientSessions()

    /**
     * Add client's websocket session.
     *
     * Do nothing if the client is already added before.
     */
    fun addClient(clientId: String, session: DefaultWebSocketServerSession) {
        if (!clients.contains(clientId)) {
            clients[clientId] = session
        }
    }

    /**
     * Remove a tracked client session.
     *
     * @return `true` if successfully removed.
     */
    fun removeClient(clientId: String): Boolean {
        return clients.remove(clientId) != null
    }

    fun getAllClients(): ClientSessions {
        return clients
    }

    /**
     * Get websocket session from [clientId].
     */
    fun getSessionFromId(clientId: String): DefaultWebSocketServerSession? {
        return clients[clientId]
    }

    /**
     * Handle websocket message from the client [session].
     */
    suspend fun handleMessage(session: DefaultWebSocketServerSession, message: WsMessage) {
        when (message.type) {
            "ping" -> {
                session.send(Frame.Text(Json.encodeToString(WsMessage(type = "ping", payload = null))))
            }
        }
    }
}
