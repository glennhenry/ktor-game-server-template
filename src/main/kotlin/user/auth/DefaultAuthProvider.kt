package user.auth

import core.data.AdminData
import data.Database
import user.PlayerAccountRepository
import user.model.UserSession
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
    override suspend fun register(username: String, password: String): UserSession {
        val pid = db.createPlayer(username, password)
        return sessionManager.create(userId = pid)
    }

    override suspend fun login(username: String, password: String): UserSession? {
        val result = playerAccountRepository.verifyCredentials(username, password)
        result.onFailure {
            Logger.error { "Failure on verifyCredentials for username=$username: ${it.message}" }
            return null
        }
        return sessionManager.create(result.getOrThrow())
    }

    override suspend fun adminLogin(): UserSession {
        return sessionManager.create(AdminData.PLAYER_ID)
    }

    override suspend fun doesUsernameExist(username: String): Boolean {
        val result = playerAccountRepository.doesUsernameExist(username)
        return result.getOrElse {
            Logger.error { "Failure on doesUsernameExist for username=$username: ${it.message}" }
            true
        }
    }

    override suspend fun doesEmailExist(email: String): Boolean {
        val result = playerAccountRepository.doesEmailExist(email)
        return result.getOrElse {
            Logger.error { "Failure on doesEmailExist for email=$email: ${it.message}" }
            true
        }
    }
}
