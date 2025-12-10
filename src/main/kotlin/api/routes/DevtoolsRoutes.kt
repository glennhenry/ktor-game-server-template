package api.routes

import context.ServerContext
import io.ktor.http.ContentType
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.webSocket
import io.ktor.util.date.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import utils.functions.UUID
import utils.logging.Logger
import ws.WsMessage
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Devtools routes. In the devtools, there are three sections of tools:
 *  - **Console**: live log from server.
 *  - **Monitor**: server status and activity from server (e.g., active players, server state, running task).
 *  - **Command**: external input to control server runtime.
 *
 * Authentication/usage flow:
 * 1. User go to `/devtools`.
 * 2. Server respond with `wall.html`, asking for token.
 * 3. User type `token` in terminal to obtain an ephemeral token (1 minute).
 * 4. Server validates the token from user (via query parameters).
 *     - If invalid, send back `wall.html` (back to step 2).
 *     - If valid, set cookie of `devtools-clientId` with a new generated token (valid for 6 hours).
 *     Then, server responds with `devtools.html`.
 * 5. Client-side connects to websocket, including the `devtools-clientId` cookie.
 * 6. Server verify the `devtools-clientId` cookie.
 *     - If invalid, refuse the connection. This will prevent arbitrary websocket connection.
 *     The page will still be valid, though client can't do anything.
 *     - If valid, then websocket connection is approved.
 *     - From now on, refreshing page does not prompt `wall.html` anymore since server
 *     will also check this cookie.
 * 7. Client and server starts exchanging WS messages for console and commands tool.
 *     - Server sends log message on server to client for console.
 *     - Client periodically make API request to `/server-status`, including the cookie token
 *     to get monitoring status.
 *         - If cookie token is invalid, server return error status.
 *         - If cookie token is valid, server return JSON for server status.
 *     - Client can type command and it will be executed in the server.
 * 8. When session exceeded 6 hours, user needs to refresh. (step 6A).
 *
 *  next todo:
 *  1. create client side
 *  2. create UI (3 toggle tabs: console, monitor, command)
 *      - console empty
 *      - monitor empty
 *      - command top left result console, under that text input, under that about and help guide,
 *      on right side help message generated
 *          - generate help message by manual template: substituting {{help}} on devtools.html
 *          with a generated HTML of all commands in command dispatcher
 *  3. upon login
 *      - ensure cookie is set
 *      - detect devtools.html
 *      - then connect to websocket
 *          - ensure cookie is included on each websocket message
 *  4. websocket manager try sending log message on console
 *  5. try sending status from monitoring
 *  6. try sending command, see if ws manager receives it, then get results
 */
fun Route.devtoolsRoutes(serverContext: ServerContext, tokenStorage: MutableMap<String, Long>) {
    get("/devtools") {
        val wallHtml = File("static/assets/wall.html")
        val devtoolsHtml = File("static/assets/devtools.html")

        // skip on developmentMode
        if (application.developmentMode) {
            Logger.debug { "Request to /devtools auth skipped (development mode)" }
            call.respondFile(devtoolsHtml)
            return@get
        }

        val token = call.request.queryParameters["token"]
        val cookie = call.request.cookies["devtools-clientId"]

        val cookieValid = cookie != null &&
                serverContext.sessionManager.verify(cookie)

        // user already authenticated before, does not need token from query parameter
        if (cookieValid) {
            Logger.debug { "Request to /devtools succeed: user has client cookie" }
            call.respondFile(devtoolsHtml)
            return@get
        }

        // user authenticating but fails
        if (token == null) {
            Logger.debug { "Request to /devtools (no token), responded with wall" }
            call.respondFile(wallHtml)
            return@get
        }

        if (!tokenStorage.contains(token)) {
            Logger.debug { "Request to /devtools: got unknown token" }
            call.respondText(insertHtmlTemplate(wallHtml, "{{MESSAGE}}", "Unknown token"), ContentType.Text.Html)
            return@get
        }

        if (tokenStorage.contains(token) && !timeUnderMinutes(tokenStorage[token]!!, 1)) {
            Logger.debug { "Request to /devtools: token already expired" }
            call.respondText(
                insertHtmlTemplate(wallHtml, "{{MESSAGE}}", "Token already expired"),
                ContentType.Text.Html
            )
            return@get
        }

        // user successful authentication: tokenValid && !cookieValid
        val session = serverContext.sessionManager.create(
            userId = UUID.new(), validFor = 6.hours, lifetime = 6.hours
        )
        call.response.cookies.append("devtools-clientId", session.token, maxAge = 21600, path = "/devtools")
        Logger.debug { "Request to /devtools: token correct, user logged in" }
        call.respondFile(devtoolsHtml)
    }

    get("/devtools/server-status") {
        call.respond("status received")
    }

    get("/cmdtest") {
        call.respondText("success!")
    }

    get("/devtools/cmd-help-text") {
        val commands = serverContext.commandDispatcher.getAllRegisteredCommands()
        val html = StringBuilder()

        html.append("<ul>")

        for (cmd in commands) {
            html.append("<li><b><code>${cmd.commandId}</code></b>: ${cmd.description}")
            html.append("<ol>")

            for (variant in cmd.variants) {
                html.append("<li>")
                html.append("<ul>")

                // Signature list
                for (sig in variant.signature) {
                    html.append("<li><code>${sig.id}</code> (<code>${sig.expectedType}</code>): ${sig.description}</li>")
                }

                html.append("</ul>")
                html.append("</li>")
            }

            html.append("</ol>")
            html.append("</li>")
        }

        html.append("</ul>")

        call.respondText(html.toString(), ContentType.Text.Html)
    }

    webSocket("/devtools/ws") {
        val token = call.request.cookies["devtools-clientId"]
        if (token == null || !serverContext.sessionManager.verify(token)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
            return@webSocket
        }

        serverContext.wsManager.addClient(token, this)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val msg = frame.readText()
                    Logger.info { "got ws: $msg" }
                    try {
                        val wsMessage = Json.decodeFromString<WsMessage>(msg)
                        if (wsMessage.type == "close") {
                            serverContext.wsManager.removeClient(token)
                            break
                        }
                        serverContext.wsManager.handleMessage(this, wsMessage)
                    } catch (e: Exception) {
                        Logger.error { "Failed to parse WS message: $msg\n$e" }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error { "Error in websocket for client $this: $e" }
        } finally {
            serverContext.wsManager.removeClient(token)
            Logger.info { "Client $this disconnected from websocket debug." }
        }
    }
}

fun timeUnderMinutes(timeMillis: Long, minutes: Int): Boolean {
    return getTimeMillis() - timeMillis < minutes.minutes.inWholeMilliseconds
}

fun insertHtmlTemplate(file: File, templateId: String, message: String): String {
    return file.readText().replace(templateId, message)
}
