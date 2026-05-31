package com.droidvisor.docker

sealed class DockerError(message: String) : RuntimeException(message) {
    class ConnectionError(message: String) : DockerError(message)
    class ApiError(message: String, val statusCode: Int) : DockerError(message)
    class ParseError(message: String) : DockerError(message)
    class TimeoutError(message: String) : DockerError(message)
    class NotFoundError(message: String) : DockerError(message)
    class ConflictError(message: String) : DockerError(message)
}