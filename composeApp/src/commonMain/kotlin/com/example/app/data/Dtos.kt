package com.example.app.data

import kotlinx.serialization.Serializable

/** Body of POST /auth/login. */
@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
)

/** The authenticated admin, as returned by the mobile API. `id` is ignored — the UI shows names. */
@Serializable
data class UserDto(
    val login: String,
    val fullName: String? = null,
    val email: String? = null,
)

/** Response of POST /auth/login. */
@Serializable
data class LoginResponse(
    val token: String,
    val expiresAt: String? = null,
    val user: UserDto,
)

/** An Agentiz project, reduced to what the list screen renders. Extra server fields are ignored. */
@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val slug: String = "",
    val description: String? = null,
    val isActive: Boolean = true,
    val repoProvider: String? = null,
)

/** Envelope every collection endpoint uses: `{ "data": [ ... ] }`. */
@Serializable
data class ProjectsResponse(
    val data: List<ProjectDto> = emptyList(),
)

/** Error envelope every endpoint uses on failure: `{ "message": "..." }`. */
@Serializable
data class ErrorResponse(
    val message: String? = null,
)
