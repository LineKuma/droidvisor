package com.droidvisor.docker

import android.util.Log
import com.droidvisor.vm.vsock.VsockService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class DockerHttpClient(private val vsockService: VsockService) {

    private val TAG = "DockerHttpClient"
    private val json = Json { ignoreUnknownKeys = true }
    private var baseUrl: String = "http://localhost:2375"
    private var useVsock: Boolean = false

    val isVsockEnabled: Boolean
        get() = useVsock

    fun setPort(port: Int) {
        baseUrl = "http://localhost:$port"
    }

    fun enableVsockMode(enabled: Boolean) {
        useVsock = enabled
        Log.d(TAG, "Vsock mode: $enabled")
    }

    suspend fun get(path: String): String {
        return executeRequest("GET", path)
    }

    suspend fun post(path: String, body: String = ""): String {
        return executeRequest("POST", path, body)
    }

    suspend fun delete(path: String): String {
        return executeRequest("DELETE", path)
    }

    private fun sanitizeLog(message: String): String {
        return message
            .replace(Regex("""\{[^}]*\}"""), "[JSON REDACTED]")
            .replace(Regex("""http[s]?://[^\s]+"""), "[URL REDACTED]")
    }

    suspend fun executeRequest(method: String, path: String, body: String = ""): String {
        return if (useVsock && vsockService.isConnected()) {
            executeVsockRequest(method, path, body)
        } else {
            executeHttpUrlRequest(method, path, body)
        }
    }

    private suspend fun executeVsockRequest(method: String, path: String, body: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val outputStream = vsockService.getOutputStream()
                    ?: throw DockerError.ConnectionError("Vsock output stream not available")
                val inputStream = vsockService.getInputStream()
                    ?: throw DockerError.ConnectionError("Vsock input stream not available")

                val request = buildHttpRequest(method, path, body)
                outputStream.write(request.toByteArray())
                outputStream.flush()

                val response = readVsockResponse(inputStream)

                if (response.statusCode in 200..299) {
                    Log.d(TAG, "Vsock request $method $path succeeded: ${response.statusCode}")
                    response.body
                } else {
                    val sanitizedError = sanitizeLog(response.body)
                    throw when (response.statusCode) {
                        404 -> DockerError.NotFoundError(sanitizedError)
                        409 -> DockerError.ConflictError(sanitizedError)
                        else -> DockerError.ApiError(sanitizedError, response.statusCode)
                    }
                }
            } catch (e: DockerError) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vsock request failed: ${e.message}", e)
                throw DockerError.ConnectionError("Vsock connection failed: ${e.message}")
            }
        }
    }

    private fun buildHttpRequest(method: String, path: String, body: String): String {
        val sb = StringBuilder()
        sb.append("$method $path HTTP/1.1\r\n")
        sb.append("Host: localhost\r\n")
        sb.append("Accept: application/json\r\n")

        if (body.isNotEmpty()) {
            val bodyBytes = body.toByteArray()
            sb.append("Content-Type: application/json\r\n")
            sb.append("Content-Length: ${bodyBytes.size}\r\n")
            sb.append("\r\n")
            sb.append(body)
        } else {
            sb.append("\r\n")
        }

        return sb.toString()
    }

    private fun readVsockResponse(inputStream: InputStream): HttpResponse {
        val headerBytes = ByteArrayOutputStream()
        var prev3 = ByteArray(4)

        while (true) {
            val b = inputStream.read()
            if (b == -1) throw DockerError.ConnectionError("Connection closed while reading headers")
            headerBytes.write(b)
            prev3 = byteArrayOf(prev3[1], prev3[2], prev3[3], b.toByte())
            if (prev3[0] == '\r'.code.toByte() && prev3[1] == '\n'.code.toByte() &&
                prev3[2] == '\r'.code.toByte() && prev3[3] == '\n'.code.toByte()) {
                break
            }
        }

        val headerStr = headerBytes.toString("UTF-8")
        val headerLines = headerStr.split("\r\n")
        val statusLine = headerLines.firstOrNull()
            ?: throw DockerError.ParseError("Empty response")

        val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull()
            ?: throw DockerError.ParseError("Invalid status line: $statusLine")

        var contentLength = -1L
        var isChunked = false
        for (line in headerLines.drop(1)) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()
                when (key) {
                    "content-length" -> contentLength = value.toLongOrNull() ?: -1L
                    "transfer-encoding" -> isChunked = value.lowercase().contains("chunked")
                }
            }
        }

        val body = when {
            isChunked -> readChunkedBody(inputStream)
            contentLength > 0 -> {
                val bodyBytes = ByteArray(contentLength.toInt())
                var offset = 0
                while (offset < bodyBytes.size) {
                    val read = inputStream.read(bodyBytes, offset, bodyBytes.size - offset)
                    if (read == -1) break
                    offset += read
                }
                String(bodyBytes, 0, offset, Charsets.UTF_8)
            }
            else -> ""
        }

        return HttpResponse(statusCode, body)
    }

    private fun readChunkedBody(inputStream: InputStream): String {
        val body = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(inputStream)
            val chunkSize = sizeLine.trim().split(";").firstOrNull()?.trim()?.toInt(16) ?: 0
            if (chunkSize == 0) break

            val chunk = ByteArray(chunkSize)
            var offset = 0
            while (offset < chunkSize) {
                val read = inputStream.read(chunk, offset, chunkSize - offset)
                if (read == -1) break
                offset += read
            }
            body.write(chunk, 0, offset)
            readLine(inputStream)
        }
        return body.toString("UTF-8")
    }

    private fun readLine(inputStream: InputStream): String {
        val sb = ByteArrayOutputStream()
        while (true) {
            val b = inputStream.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) sb.write(b)
        }
        return sb.toString("UTF-8")
    }

    private suspend fun executeHttpUrlRequest(method: String, path: String, body: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl$path")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = method
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 30000

                if (body.isNotEmpty()) {
                    connection.doOutput = true
                    connection.outputStream.write(body.toByteArray())
                    connection.outputStream.flush()
                }

                val responseCode = connection.responseCode
                val responseBody = readStream(connection.inputStream)

                if (responseCode in 200..299) {
                    Log.d(TAG, "Request $method $path succeeded: $responseCode")
                    responseBody
                } else {
                    val errorBody = readStream(connection.errorStream)
                    val sanitizedError = sanitizeLog(errorBody)
                    throw when (responseCode) {
                        404 -> DockerError.NotFoundError(sanitizedError)
                        409 -> DockerError.ConflictError(sanitizedError)
                        else -> DockerError.ApiError(sanitizedError, responseCode)
                    }
                }
            } catch (e: DockerError) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "HTTP request failed: ${e.message}", e)
                throw DockerError.ConnectionError("Connection failed")
            }
        }
    }

    suspend fun <T> getJson(path: String, deserializer: (String) -> T): T {
        val response = get(path)
        return try {
            deserializer(response)
        } catch (e: SerializationException) {
            throw DockerError.ParseError("Failed to parse response")
        }
    }

    private fun readStream(inputStream: InputStream?): String {
        inputStream ?: return ""
        ByteArrayOutputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
            return outputStream.toString("UTF-8")
        }
    }

    private data class HttpResponse(val statusCode: Int, val body: String)
}
