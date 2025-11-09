package user

import data.collection.PlayerAccount

/**
 * Empty implementation (no operation) of [PlayerAccountRepository] only used for testing purposes.
 */
class EmptyPlayerAccountRepository : PlayerAccountRepository {
    override suspend fun doesUserExist(username: String): Result<Boolean> = TODO("ONLY TEST")
    override suspend fun getPlayerAccountByName(username: String): Result<PlayerAccount?> = TODO("ONLY TEST")
    override suspend fun getPlayerAccountById(playerId: String): Result<PlayerAccount?> = TODO("ONLY TEST")
    override suspend fun getPlayerIdFromName(username: String): Result<String?> = TODO("ONLY TEST")
    override suspend fun updatePlayerAccount(playerId: String, account: PlayerAccount): Result<Unit> = TODO("ONLY TEST")
    override suspend fun updateLastLogin(playerId: String, lastLogin: Long): Result<Unit> = TODO("ONLY TEST")
    override suspend fun verifyCredentials(username: String, password: String): Result<String> = TODO("ONLY TEST")
}
