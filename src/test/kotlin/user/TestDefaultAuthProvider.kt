package user

import com.mongodb.assertions.Assertions.assertFalse
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import data.MongoImpl
import data.collection.PlayerAccount
import example.ExampleModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.bson.Document
import user.auth.SessionManager
import user.auth.DefaultAuthProvider
import user.model.ServerMetadata
import user.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration test for [DefaultAuthProvider] and [PlayerAccountRepository].
 *
 * Ensure MongoDB is running.
 */
class TestDefaultAuthProvider {
    @Test
    fun `test doesUserExist for registered user return true`() = runTest {
        val mongoDb = initMongo()
        val collection = mongoDb.getCollection<PlayerAccount>("player_account")
        collection.drop()
        mongoDb.createCollection("test_player_account")

        val db = MongoImpl(mongoDb, false)
        val manager = SessionManager()
        val repo = PlayerAccountRepositoryMongo(collection)
        val provider = DefaultAuthProvider(db, repo, manager)

        val account = PlayerAccount(
            playerId = "pid12345",
            hashedPassword = "hashed123",
            profile = UserProfile.default("pid12345", "name"),
            metadata = ServerMetadata()
        )
        collection.insertOne(account)

        assertTrue(provider.doesUserExist("name"))
    }

    @Test
    fun `test doesUserExist for unregistered user return true`() = runTest {
        val mongoDb = initMongo()
        val collection = mongoDb.getCollection<PlayerAccount>("player_account")
        collection.drop()
        mongoDb.createCollection("test_player_account")

        val db = MongoImpl(mongoDb, false)
        val manager = SessionManager()
        val repo = PlayerAccountRepositoryMongo(collection)
        val provider = DefaultAuthProvider(db, repo, manager)

        assertFalse(provider.doesUserExist("xyz"))
    }

    @Test
    fun `test register successfully create user`() = runTest {
        val mongoDb = initMongo()
        val collection = mongoDb.getCollection<PlayerAccount>("player_account")
        collection.drop()
        mongoDb.createCollection("test_player_account")

        val db = MongoImpl(mongoDb, false)
        val manager = SessionManager()
        val repo = PlayerAccountRepositoryMongo(collection)
        val provider = DefaultAuthProvider(db, repo, manager)

        provider.register("helloworld", "kotlinktor")
        assertTrue(provider.doesUserExist("helloworld"))
    }

    @Test
    fun `test login but account don't exist return null`() = runTest {
        val mongoDb = initMongo()
        val collection = mongoDb.getCollection<PlayerAccount>("player_account")
        collection.drop()
        mongoDb.createCollection("test_player_account")

        val db = MongoImpl(mongoDb, false)
        val manager = SessionManager()
        val repo = PlayerAccountRepositoryMongo(collection)
        val provider = DefaultAuthProvider(db, repo, manager)

        assertNull(provider.login("asdf", "fdsa"))
    }

    @Test
    fun `test login wrong credentials return null`() = runTest {
        val mongoDb = initMongo()
        val collection = mongoDb.getCollection<PlayerAccount>("player_account")
        collection.drop()
        mongoDb.createCollection("test_player_account")

        val db = MongoImpl(mongoDb, false)
        val manager = SessionManager()
        val repo = PlayerAccountRepositoryMongo(collection)
        val provider = DefaultAuthProvider(db, repo, manager)

        provider.register("helloworld", "kotlinktor")
        assertNull(provider.login("helloworld", "ktor"))
    }

    @Test
    fun `test login user registered and correct credentials return non-null`() = runTest {
        val mongoDb = initMongo()
        val collection = mongoDb.getCollection<PlayerAccount>("player_account")
        collection.drop()
        mongoDb.createCollection("test_player_account")

        val db = MongoImpl(mongoDb, false)
        val manager = SessionManager()
        val repo = PlayerAccountRepositoryMongo(collection)
        val provider = DefaultAuthProvider(db, repo, manager)

        provider.register("helloworld", "kotlinktor")
        assertNotNull(provider.login("helloworld", "kotlinktor"))
    }

    private suspend fun initMongo(): MongoDatabase {
        val mongoc = MongoClient.create("mongodb://localhost:27017")
        val db = mongoc.getDatabase("test")
        db.runCommand(Document("ping", 1))
        return db
    }
}
