package me.bechberger.jfrplugin.viewer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import me.bechberger.jfrplugin.util.PsiUtils
import java.net.ServerSocket
import java.net.Socket
import java.util.logging.Logger

/**
 * Runs a minimal HTTP server that receives "Open in IDE" jump requests from Jeffrey.
 *
 * Jeffrey POSTs to POST /ide/<fqn>.<method> with body {"method":"ClassName.method","line":N}.
 * We parse the FQN from the path, extract the line from the body, and navigate via PsiUtils.
 */
@Service(Service.Level.PROJECT)
class JeffreyIdeJumpService(private val project: Project) {

    private val LOG = Logger.getLogger("JeffreyIdeJumpService")
    private var serverSocket: ServerSocket? = null

    val port: Int get() = serverSocket?.localPort ?: -1

    fun start() {
        if (serverSocket?.isClosed == false) return
        val ss = ServerSocket(0)
        serverSocket = ss
        LOG.info("JeffreyIdeJumpService listening on port ${ss.localPort}")
        ApplicationManager.getApplication().executeOnPooledThread {
            while (!ss.isClosed) {
                try {
                    val conn = ss.accept()
                    ApplicationManager.getApplication().executeOnPooledThread { handleConnection(conn) }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
    }

    private fun handleConnection(conn: Socket) {
        conn.use {
            try {
                val reader = conn.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                var contentLength = 0
                var line = reader.readLine()
                while (line != null && line.isNotBlank()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }

                val body = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    String(buf)
                } else ""

                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    respond(conn, 400, "Bad Request")
                    return
                }

                val method = parts[0]
                val path = parts[1]

                if (!path.startsWith("/ide/")) {
                    respond(conn, 404, "Not Found")
                    return
                }

                // GET /ide/has/<fqn> or GET /ide/has/<fqn>#<method>
                if (method == "GET" && path.startsWith("/ide/has/")) {
                    val raw = java.net.URLDecoder.decode(path.removePrefix("/ide/has/"), "UTF-8")
                    val hashIdx = raw.indexOf('#')
                    if (hashIdx >= 0) {
                        val fqn = raw.substring(0, hashIdx)
                        val methodSig = raw.substring(hashIdx + 1)
                        val resolved = PsiUtils.hasMethod(project, fqn, methodSig)
                        respondJson(conn, """{"resolved":$resolved}""")
                    } else {
                        val resolved = PsiUtils.hasClass(project, raw)
                        respondJson(conn, """{"resolved":$resolved}""")
                    }
                    return
                }

                if (method != "POST") {
                    respond(conn, 405, "Method Not Allowed")
                    return
                }

                // Path: /ide/com.example.Foo.methodName — last segment is method, rest is FQN
                val fqnAndMethod = path.removePrefix("/ide/").let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                }
                val lastDot = fqnAndMethod.lastIndexOf('.')
                if (lastDot < 0) {
                    respond(conn, 400, "Bad Request")
                    return
                }
                val fqn = fqnAndMethod.substring(0, lastDot)        // com.example.Foo
                val methodName = fqnAndMethod.substring(lastDot + 1) // methodName

                val lineNumber = extractJsonInt(body, "line") ?: 1

                // Split FQN into package + simple class name
                val classDot = fqn.lastIndexOf('.')
                val pkg = if (classDot >= 0) fqn.substring(0, classDot) else ""
                val simpleName = if (classDot >= 0) fqn.substring(classDot + 1) else fqn

                LOG.info("IDE jump: $fqn.$methodName line $lineNumber")
                ApplicationManager.getApplication().invokeLater {
                    PsiUtils.navigateToClass(project, simpleName, pkg, lineNumber, methodName)
                }

                respond(conn, 200, "OK")
            } catch (e: Exception) {
                LOG.warning("JeffreyIdeJumpService error: ${e.message}")
                try { respond(conn, 500, "Internal Server Error") } catch (_: Exception) {}
            }
        }
    }

    private fun respond(conn: Socket, status: Int, message: String) {
        val body = message.toByteArray()
        val response = "HTTP/1.1 $status $message\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        conn.outputStream.write(response.toByteArray())
        conn.outputStream.write(body)
        conn.outputStream.flush()
    }

    private fun respondJson(conn: Socket, json: String) {
        val body = json.toByteArray()
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        conn.outputStream.write(response.toByteArray())
        conn.outputStream.write(body)
        conn.outputStream.flush()
    }

    private fun extractJsonInt(json: String, key: String): Int? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    companion object {
        fun getInstance(project: Project): JeffreyIdeJumpService = project.service()
    }
}
