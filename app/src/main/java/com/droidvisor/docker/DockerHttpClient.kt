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

    fun setPort(port: Int) {
        baseUrl = "http://localhost:$port"
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

    suspend fun executeRequest(method: String, path: String, body: String = ""): String {
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
                    throw when (responseCode) {
                        404 -> DockerError.NotFoundError(errorBody)
                        409 -> DockerError.ConflictError(errorBody)
                        else -> DockerError.ApiError(errorBody, responseCode)
                    }
                }
            } catch (e: DockerError) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "HTTP request failed", e)
                throw DockerError.ConnectionError(e.message ?: "Connection failed")
            }
        }
    }

    suspend fun <T> getJson(path: String, deserializer: (String) -> T): T {
        val response = get(path)
        return try {
            deserializer(response)
        } catch (e: SerializationException) {
            throw DockerError.ParseError("Failed to parse response: ${e.message}")
        }
    }

    private fun readStream(inputStream: InputStream?): String {
        inputStream ?: return ""
        ByteArrayOutputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
            return outputStream.toString("UTF-8")
        }
    }
}