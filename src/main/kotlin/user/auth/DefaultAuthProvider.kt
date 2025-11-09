package user.auth

import core.data.AdminData
import data.Database
import user.PlayerAccountRepository
import user.model.PlayerSession
import utils.logging.Logger

/**
 * Default auth provider where authentication is handled typically.
 *
 * This should be replaced or modified later.
 */
class DefaultAuthProvider(
    private val db: Database,
    private val playerAccountRepository: PlayerAccountRepository,
    private val sessionManager: SessionManager
) : AuthProvider {
    override suspend fun register(username: String, password: String): PlayerSession {
        val pid = db.createPlayer(username, password)
        return sessionManager.create(playerId = pid)
    }

    override suspend fun login(username: String, password: String): PlayerSession? {
        val result = playerAccountRepository.verifyCredentials(username, password)
        result.onFailure {
            Logger.error { "Failure on verifyCredentials for username=$username: ${it.message}" }
            return null
        }
        return sessionManager.create(result.getOrThrow())
    }

    override suspend fun adminLogin(): PlayerSession {
        return sessionManager.create(AdminData.PLAYER_ID)
    }

    override suspend fun doesUserExist(username: String): Boolean {
        val result = playerAccountRepository.doesUserExist(username)
        result.onFailure {
            Logger.error { "Failure on doesUserExist for username=$username: ${it.message}" }
        }
        return result.getOrThrow()
    }
}
