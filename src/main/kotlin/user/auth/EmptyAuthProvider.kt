package user.auth

import user.model.UserSession

/**
 * Empty auth provider (no operation) only used for testing purpose.
 */
class EmptyAuthProvider : AuthProvider {
    override suspend fun register(username: String, password: String): UserSession = TODO("ONLY TEST")
    override suspend fun login(username: String, password: String): UserSession = TODO("ONLY TEST")
    override suspend fun adminLogin(): UserSession = TODO("ONLY TEST")
    override suspend fun doesUsernameExist(username: String): Boolean = TODO("ONLY TEST")
    override suspend fun doesEmailExist(email: String): Boolean = TODO("ONLY TEST")
}
