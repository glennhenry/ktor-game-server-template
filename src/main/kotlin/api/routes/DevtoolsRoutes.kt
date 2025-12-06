package api.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import java.io.File

fun Route.devtoolsRoutes(tokenStorage: MutableMap<String, Long>) {
    get("/devtools") {
        val wallHtml = File("static/assets/wall.html")
        val devtoolsHtml = File("static/assets/devtools.html")

        val token = call.request.queryParameters["token"]

        val tokenValid = token != null &&
                tokenStorage.contains(token) &&
                underOneMinute(tokenStorage[token]!!)

        if (!tokenValid) {
            call.respondFile(wallHtml)
            return@get
        }

        call.respondFile(devtoolsHtml)
    }
}

fun underOneMinute(timeMillis: Long): Boolean {
    return getTimeMillis() - timeMillis < 60000
}
