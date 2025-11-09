package user.auth

import user.model.PlayerSession
import core.data.AdminData
import kotlinx.coroutines.CoroutineDispatcher
import utils.functions.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import utils.functions.SystemTime
import utils.functions.TimeProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages authentication sessions for online players, which are valid for a maximum of 6 hours.
 *
 * A player's session is identified by `playerId` and verification is done with a unique UUID token.
 *
 * Individual sessions have a **1-hour timeout** but can be refreshed by the player to extend
 * the total session lifetime up to the **6-hour maximum**.
 */
class SessionManager(
    private val time: TimeProvider = SystemTime,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    private val CLEANUP_INTERVAL_MS = 5 * 60 * 1000L       // 5 minutes
    private val SESSION_DURATION_MS = 1 * 60 * 60 * 1000L  // 1 hours
    private val SESSION_LIFETIME_MS = 6 * 60 * 60 * 1000L  // 6 hours
    private val cleanupJob = Job()
    private val scope = CoroutineScope(dispatcher + cleanupJob)

    init {
        scope.launch {
            while (isActive) {
                cleanupExpiredSessions()
                delay(CLEANUP_INTERVAL_MS)
            }
        }
    }

    /**
     * Create session for the [playerId] with the duration of 1 hours and lifetime of 6 hours.
     */
    fun create(playerId: String): PlayerSession {
        val now = time.now()

        val token = if (playerId == AdminData.PLAYER_ID) {
            AdminData.TOKEN
        } else {
            UUID.new()
        }

        val session = PlayerSession(
            playerId = playerId,
            token = token,
            issuedAt = now,
            expiresAt = now + SESSION_DURATION_MS,
            lifetime = SESSION_LIFETIME_MS
        )

        sessions[token] = session
        return session
    }

    /**
     * Verify player's session validity from the given [token].
     *
     * This checks whether the token is valid
     * (i.e., the token was issued before and doesn't expire yet).
     *
     * @return `true` if session is valid
     */
    fun verify(token: String): Boolean {
        val session = sessions[token] ?: return false
        val now = time.now()

        return now < session.expiresAt
    }

    /**
     * Refresh player's session from the given [token].
     *
     * First, it checks whether the token is valid
     * (i.e., the token was issued before and doesn't exceed the maximum lifetime).
     *
     * @return `true` if session was successfully refreshed.
     */
    fun refresh(token: String): Boolean {
        val session = sessions[token] ?: return false
        val now = time.now()

        val lifetime = now - session.issuedAt
        if (lifetime > SESSION_LIFETIME_MS) {
            sessions.remove(token)
            return false
        }

        session.expiresAt = now + SESSION_DURATION_MS
        return true
    }

    /**
     * Get the `playerId` associated with this [token].
     *
     * @return `null` if the token is invalid.
     */
    fun getPlayerId(token: String): String? {
        return sessions[token]?.takeIf { time.now() < it.expiresAt }?.playerId
    }

    /**
     * Cleanup expired sessions, which exceeded the maximum lifetime.
     */
    private fun cleanupExpiredSessions() {
        val now = time.now()
        val expiredKeys = sessions.filterValues { now - it.issuedAt > SESSION_LIFETIME_MS }.keys
        expiredKeys.forEach { sessions.remove(it) }
    }

    fun shutdown() {
        sessions.clear()
        cleanupJob.cancel()
    }
}
