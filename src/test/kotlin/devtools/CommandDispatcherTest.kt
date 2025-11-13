package devtools

import context.ServerContext
import devtools.cmd.ArgumentInfo
import devtools.cmd.Command
import devtools.cmd.CommandDispatcher
import devtools.cmd.CommandRequest
import devtools.cmd.CommandResult
import devtools.cmd.CommandType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.assertThrows
import utils.JSON
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Command dispatcher test
 */
class CommandDispatcherTest {
    private val context = ServerContext.fake()

    @BeforeTest
    fun setupJson() {
        JSON.initialize(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    @Test
    fun `CommandDispatcher execute failed when command is not registered`() = runTest {
        val dispatcher = CommandDispatcher(context)

        val obj = buildJsonObject {
            put("field1", JsonPrimitive("pid123"))
            put("field2", JsonPrimitive(1))
        }

        val input = CommandRequest("Example", obj)

        assertTrue(dispatcher.handleCommand(input) is CommandResult.CommandNotFound)
    }

    @Test
    fun `CommandDispatcher register throws on duplicate command`() = runTest {
        val dispatcher = CommandDispatcher(context)
        dispatcher.register(ExampleCommand())

        assertThrows<IllegalArgumentException> {
            dispatcher.register(ExampleCommand())
        }
    }

    @Test
    fun `CommandDispatcher successfully execute perfect command`() = runTest {
        val dispatcher = CommandDispatcher(context)
        dispatcher.register(ExampleCommand())

        val obj = buildJsonObject {
            put("field1", JsonPrimitive("pid123"))
            put("field2", JsonPrimitive(12))
            put("field3", JsonPrimitive(true))
        }

        val input = CommandRequest("Example", obj)

        assertEquals(CommandResult.Executed, dispatcher.handleCommand(input))
    }

    @Test
    fun `CommandDispatcher successfully execute command with unprovided optional`() = runTest {
        val dispatcher = CommandDispatcher(context)
        dispatcher.register(ExampleCommand())

        val obj = buildJsonObject {
            put("field1", JsonPrimitive("pid123"))
            put("field2", JsonPrimitive(12))
        }

        val input = CommandRequest("Example", obj)

        assertEquals(CommandResult.Executed, dispatcher.handleCommand(input))
    }

    @Test
    fun `CommandDispatcher execute failed with unprovided required value`() = runTest {
        val dispatcher = CommandDispatcher(context)
        dispatcher.register(ExampleCommand())

        val obj = buildJsonObject {
            put("field2", JsonPrimitive(12))
        }

        val input = CommandRequest("Example", obj)

        assertTrue(dispatcher.handleCommand(input) is CommandResult.InvalidArgument)
    }

    @Test
    fun `CommandDispatcher execute success on string argument type mismatch`() = runTest {
        val dispatcher = CommandDispatcher(context)
        dispatcher.register(ExampleCommand())

        val obj = buildJsonObject {
            put("field1", JsonPrimitive(12))
            put("field2", JsonPrimitive(12))
        }

        val input = CommandRequest("Example", obj)

        assertEquals(CommandResult.Executed, dispatcher.handleCommand(input))
    }

    @Test
    fun `CommandDispatcher execute failed on non-string argument type mismatch`() = runTest {
        val dispatcher = CommandDispatcher(context)
        dispatcher.register(ExampleCommand())

        val obj = buildJsonObject {
            put("field1", JsonPrimitive("pid123"))
            put("field2", JsonPrimitive("dsafdsf"))
        }

        val input = CommandRequest("Example", obj)

        assertTrue(dispatcher.handleCommand(input) is CommandResult.InvalidArgument)
    }

    @Test
    fun `CommandDispatcher execute failed when command execution throws`() = runTest {
        val dispatcher = CommandDispatcher(context)
        dispatcher.register(ExampleCommand())

        val obj = buildJsonObject {
            put("field1", JsonPrimitive("pid123"))
            put("field2", JsonPrimitive(1))
        }

        val input = CommandRequest("Example", obj)

        assertTrue(dispatcher.handleCommand(input) is CommandResult.Error)
    }
}

@Serializable
data class ExampleArgument(
    val field1: String,
    val field2: Int,
    val field3: Boolean = false,
)

class ExampleCommand : Command<ExampleArgument> {
    override val name: String = "Example"
    override val description: String = "This is just an example command"
    override val completionMessage: String = "Item {} successfully given to {}"
    override val serializer: KSerializer<ExampleArgument> = ExampleArgument.serializer()
    override val argInfo: Map<String, ArgumentInfo> = mapOf(
        "field1" to ArgumentInfo(
            name = "field1",
            description = "Field 1 controls something",
            required = true,
            defaultValue = null,
            type = CommandType.String
        ),
        "field2" to ArgumentInfo(
            name = "field2",
            description = "Field 2 controls something",
            required = true,
            defaultValue = null,
            type = CommandType.Int
        ),
        "field3" to ArgumentInfo(
            name = "field3",
            description = "Field 3 controls something",
            required = false,
            defaultValue = "false",
            type = CommandType.Boolean
        ),
    )

    override suspend fun execute(serverContext: ServerContext, arg: ExampleArgument) {
        if (arg.field2 == 1) {
            throw Exception()
        }
        println("Executed with arg=$arg")
    }
}
