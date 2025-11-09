package server.messaging

/**
 * A template for socket message handler.
 */
interface SocketMessageHandler {
    val name: String

    /**
     * To determine whether the handler should handle the given [SocketMessage].
     */
    fun <T> match(message: SocketMessage<T>): Boolean

    /**
     * Handle the socket message with the given [HandlerContext].
     *
     * Handler may send message to client multiple times using the `send` function.
     */
    suspend fun handle(ctx: HandlerContext)
}
