package api.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File

fun Route.fileRoutes() {
    get("/") {
        val indexFile = File("static/assets/index.html")
        if (indexFile.exists()) {
            call.respondFile(indexFile)
        } else {
            call.respond(HttpStatusCode.NotFound, "Index HTML not found")
        }
    }

    get("/docs") {
        val docsIndex = File("docs/index.html")
        if (docsIndex.exists()) {
            call.respondFile(docsIndex)
        } else {
            call.respond(HttpStatusCode.NotFound, "Only available in production; Please start the docs using vite server or build the server first.")
        }
    }

    staticFiles("docs", File("docs"))

    get("/favicon.ico") {
        val favicon = File("static/assets/favicon.ico")
        call.respondFile(favicon)
    }
}
