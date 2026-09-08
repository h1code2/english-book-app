package org.h1code2.english.notebook.sync

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * 极简局域网 HTTP 接收服务（无第三方依赖）。
 *
 * 路由：
 *   GET  /ping   → 校验令牌 + 存活探测
 *   POST /add    → 推送一条记录
 *   OPTIONS 任意 → CORS 预检
 *
 * 鉴权：请求头 X-Token 必须与服务端令牌一致。
 * 单连接单请求（Connection: close），顺序解析，线程池处理并发。
 */
class SyncServer(
    private val port: Int,
    @Volatile var token: String,
    private val onAdd: (String) -> PushProtocol.AddRequest,   // 解析（可抛异常）
    private val onStore: (PushProtocol.AddRequest) -> StoreResult // 入库（阻塞执行）
) {

    data class StoreResult(val added: Int, val duplicates: Int)

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var running = false

    var actualPort: Int = port
        private set

    fun start(): Boolean {
        return try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(port))
            serverSocket = ss
            actualPort = ss.localPort
            running = true
            Thread({ acceptLoop() }, "sync-server-accept").apply { isDaemon = true }.start()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        executor.shutdownNow()
    }

    private fun acceptLoop() {
        while (running) {
            val client = try {
                serverSocket?.accept() ?: break
            } catch (_: Exception) {
                break
            }
            executor.execute {
                try {
                    handle(client)
                } catch (_: Exception) {
                } finally {
                    try {
                        client.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 8000
        val ins = socket.getInputStream()
        val head = readUntilDoubleCrlf(ins)
        val headText = String(head, Charsets.UTF_8)
        val lines = headText.split("\r\n")
        val requestLine = lines.firstOrNull() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val rawPath = parts[1]
        val path = rawPath.substringBefore('?')

        val headers = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }

        // CORS 预检直接放行
        if (method == "OPTIONS") {
            respond(socket.getOutputStream(), 200, "{}")
            return
        }

        // 令牌校验（所有业务路由）
        val requestToken = headers["x-token"] ?: ""
        if (requestToken != token) {
            respond(socket.getOutputStream(), 401, PushProtocol.errorResponse("令牌不正确"))
            return
        }

        when (method to path) {
            "GET" to "/ping" -> respond(socket.getOutputStream(), 200, PushProtocol.pingResponse())

            "POST" to "/add" -> {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength <= 0 || contentLength > MAX_BODY) {
                    respond(socket.getOutputStream(), 400, PushProtocol.errorResponse("请求体大小异常"))
                    return
                }
                val body = String(readFully(ins, contentLength), Charsets.UTF_8)
                val result = try {
                    val request = onAdd(body)
                    val store = onStore(request)
                    PushProtocol.okResponse(store.added, store.duplicates)
                } catch (e: IllegalArgumentException) {
                    PushProtocol.errorResponse(e.message ?: "请求格式错误")
                } catch (e: Exception) {
                    PushProtocol.errorResponse("入库失败：${e.message}")
                }
                val status = if (result.contains("\"ok\":true")) 200 else 400
                respond(socket.getOutputStream(), status, result)
            }

            else -> respond(socket.getOutputStream(), 404, PushProtocol.errorResponse("未知路径 $path"))
        }
    }

    private fun respond(out: OutputStream, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; else -> "Error"
        }
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Content-Type, X-Token\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val MAX_HEADER = 16 * 1024
        const val MAX_BODY = 1024 * 1024

        /** 读取到 \r\n\r\n 为止（含），用于请求头 */
        private fun readUntilDoubleCrlf(ins: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            var matched = 0
            while (out.size() < MAX_HEADER) {
                val b = ins.read()
                if (b < 0) break
                out.write(b)
                matched = when {
                    b == 13 && (matched == 0 || matched == 2) -> matched + 1
                    b == 10 && (matched == 1 || matched == 3) -> matched + 1
                    b == 13 -> 1          // 异常序列中再次出现 \r，重新起匹配
                    else -> 0
                }
                if (matched == 4) break
            }
            return out.toByteArray()
        }

        private fun readFully(ins: InputStream, n: Int): ByteArray {
            val buf = ByteArray(n)
            var read = 0
            while (read < n) {
                val r = ins.read(buf, read, n - read)
                if (r < 0) break
                read += r
            }
            return buf
        }
    }
}
