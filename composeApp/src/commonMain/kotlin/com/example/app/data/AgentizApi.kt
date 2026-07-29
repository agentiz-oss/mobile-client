package com.example.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** A non-2xx response from the mobile API, carrying the server's `message` when there is one. */
class ApiException(val status: Int, message: String) : Exception(message)

/**
 * Thin client over the `app-agentiz-mobile-api` layer. One instance owns one [HttpClient]; the
 * base origin is fixed at construction so the login screen can point the app at any server.
 */
class AgentizApi(baseUrl: String = platformDefaultBaseUrl()) {

    /** Absolute prefix of every call, e.g. `http://localhost:17280/api/agentiz/mobile/v1`. */
    private val root = baseUrl.trimEnd('/') + BASE_PATH

    private val client = HttpClient {
        // Handle error status codes ourselves so we can surface the server's message.
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /** Exchanges admin credentials for a bearer token. Throws [ApiException] on bad credentials. */
    suspend fun login(login: String, password: String): LoginResponse =
        client.post("$root/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(login = login, password = password))
        }.decodeOrThrow()

    /** Projects owned by the token's user. */
    suspend fun projects(token: String): List<ProjectDto> =
        client.get("$root/projects") {
            bearerAuth(token)
        }.decodeOrThrow<ProjectsResponse>().data

    /** Releases the underlying engine; call when the client is no longer needed. */
    fun close() = client.close()

    private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
        if (status.isSuccess()) return body()
        val serverMessage = runCatching { body<ErrorResponse>().message }.getOrNull()
        throw ApiException(status.value, serverMessage ?: "Request failed (HTTP ${status.value})")
    }

    companion object {
        const val BASE_PATH = "/api/agentiz/mobile/v1"
    }
}
