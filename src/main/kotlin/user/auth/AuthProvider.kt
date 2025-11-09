package user.auth

import user.model.PlayerSession

/**
 * Define component that provide authentication functionality.
 */
interface AuthProvider {
    /**
     * Register a new account with [username] and [password].
     *
     * @return [PlayerSession] of the newly created account for further authentication.
     *         Returns `null` if registration is failed.
     */
    suspend fun register(username: String, password: String): PlayerSession?

    /**
     * Login with [username] and [password].
     *
     * @return [PlayerSession] which is used for further authentication.
     *         Returns `null` if login is failed (i.e., wrong credentials or user doesn't exist).
     */
    suspend fun login(username: String, password: String): PlayerSession?

    /**
     * Login with admin account, should always succeed.
     */
    suspend fun adminLogin(): PlayerSession

    /**
     * Check whether a user with [username] exists.
     */
    suspend fun doesUserExist(username: String): Boolean
}
