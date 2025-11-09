package user.auth

import user.model.PlayerSession

/**
 * Empty auth provider (no operation) only used for testing purpose.
 */
class EmptyAuthProvider : AuthProvider {
    override suspend fun register(username: String, password: String): PlayerSession = TODO("ONLY TEST")
    override suspend fun login(username: String, password: String): PlayerSession = TODO("ONLY TEST")
    override suspend fun adminLogin(): PlayerSession = TODO("ONLY TEST")
    override suspend fun doesUserExist(username: String): Boolean = TODO("ONLY TEST")
}
