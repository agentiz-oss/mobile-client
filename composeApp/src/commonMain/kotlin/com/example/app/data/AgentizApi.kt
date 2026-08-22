package com.example.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A non-2xx response from the mobile API, carrying the server's `message` when there is one. */
class ApiException(val status: Int, message: String) : Exception(message)

/**
 * Thin client over the `app-agentiz-mobile-api` layer. One instance owns one [HttpClient]; the
 * base origin is fixed at construction so the login screen can point the app at any server.
 */
class AgentizApi(baseUrl: String = platformDefaultBaseUrl()) {

    /** Absolute prefix of every call, e.g. `https://agentiz.m42.cx/api/agentiz/mobile/v1`. */
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

    /**
     * The token's user as the server sees it *now* — most importantly a fresh timezone offset:
     * the stored session keeps the offset from login day, and DST or a profile edit moves it.
     */
    suspend fun me(token: String): UserDto =
        client.get("$root/auth/me") {
            bearerAuth(token)
        }.decodeOrThrow<MeResponse>().user

    /**
     * Mints a short-lived, one-use WebView URL for the Agentiz Assistant.
     *
     * The mobile bearer token is used only for this exchange.  Loading the returned URL lets the
     * server install its HttpOnly Adminizer session cookie, so neither the bearer nor a dashboard
     * cookie is ever put into the WebView URL or exposed to page JavaScript.
     */
    suspend fun assistantWebviewSession(token: String): AssistantWebviewSessionDto =
        client.post("$root/assistant/webview-session") {
            bearerAuth(token)
        }.decodeOrThrow()

    /** Projects owned by the token's user. */
    suspend fun projects(token: String): List<ProjectDto> =
        client.get("$root/projects") {
            bearerAuth(token)
        }.decodeOrThrow<ProjectsResponse>().data

    /** Tasks of one project, newest first. */
    suspend fun tasks(token: String, projectId: String): List<TaskDto> =
        client.get("$root/projects/$projectId/tasks") {
            bearerAuth(token)
        }.decodeOrThrow<TasksResponse>().data

    suspend fun createTask(
        token: String,
        projectId: String,
        title: String,
        description: String?,
        tags: List<String> = emptyList(),
    ): TaskDto =
        client.post("$root/projects/$projectId/tasks") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateTaskRequest(title = title, description = description, tags = tags))
        }.decodeOrThrow<TaskResponse>().data

    /** One task with its latest run and the whole comment thread. */
    suspend fun task(token: String, taskId: String): TaskDetailDto =
        client.get("$root/tasks/$taskId") {
            bearerAuth(token)
        }.decodeOrThrow<TaskDetailResponse>().data

    /**
     * Queues a pipeline run. The server answers as soon as the job is enqueued, not when it
     * finishes — a worker executes it out of band, so the caller polls [task] for the outcome.
     */
    suspend fun runTask(token: String, taskId: String, choice: RunTaskRequest = RunTaskRequest()): RunRefDto =
        client.post("$root/tasks/$taskId/run") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(choice)
        }.decodeOrThrow<RunRefResponse>().data

    /**
     * What this task's launch may choose, and what it gets untouched. Loaded once with the task
     * rather than on every poll: it changes only when an operator edits the pipeline or a worker's
     * runners.
     */
    suspend fun runOptions(token: String, taskId: String): RunOptionsDto =
        client.get("$root/tasks/$taskId/run-options") {
            bearerAuth(token)
        }.decodeOrThrow<RunOptionsResponse>().data

    /** Compact run history, newest first. Use [run] to load a run's trace. */
    suspend fun runs(token: String, taskId: String): List<RunDto> =
        client.get("$root/tasks/$taskId/runs") {
            bearerAuth(token)
        }.decodeOrThrow<RunsResponse>().data

    /**
     * Every run in flight across all the user's projects, plus the ones that finished most
     * recently. Polled while the board is open — a run's state changes without the app asking.
     */
    suspend fun runBoard(token: String): RunBoardDto =
        client.get("$root/runs") {
            bearerAuth(token)
        }.decodeOrThrow<RunBoardResponse>().data

    /** Full result, stages and log of one historical run. */
    suspend fun run(token: String, taskId: String, runId: String): RunDto =
        client.get("$root/tasks/$taskId/runs/$runId") {
            bearerAuth(token)
        }.decodeOrThrow<RunResponse>().data

    /**
     * Closes (or reopens) a task by hand — the exit for the two inbox rows nothing local resolves:
     * an opened pull request and a run that will not be retried. The server refuses the statuses
     * the pipeline owns, so a stale button cannot fake "running".
     */
    suspend fun setTaskStatus(token: String, taskId: String, status: String): TaskDto =
        client.post("$root/tasks/$taskId/status") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(TaskStatusRequest(status))
        }.decodeOrThrow<TaskResponse>().data

    /**
     * Applies a diff the pipeline held back for approval. Same call the panel's button makes; a
     * second press comes back as a 409 rather than a second commit.
     */
    suspend fun applyRunDiff(token: String, taskId: String, runId: String) {
        client.post("$root/tasks/$taskId/runs/$runId/apply") {
            bearerAuth(token)
        }.decodeOrThrow<ApplyDiffResponse>()
    }

    /** Requests cancellation of a queued or running run. */
    suspend fun cancelRun(token: String, taskId: String, runId: String): RunDto =
        client.post("$root/tasks/$taskId/runs/$runId/cancel") {
            bearerAuth(token)
        }.decodeOrThrow<RunResponse>().data

    /**
     * Every question an agent is currently waiting on, across all the user's projects. Each one has
     * a run parked in `waiting_input` behind it, so this is polled rather than fetched once.
     */
    suspend fun pendingInteractions(token: String): List<InteractionDto> =
        client.get("$root/interactions") {
            bearerAuth(token)
        }.decodeOrThrow<InteractionsResponse>().data

    /**
     * Answers one question and lets its run continue. `accept` carries the filled-in form, which the
     * server validates against the question's own `requestedSchema` — a mismatch comes back as an
     * [ApiException] naming the offending fields. `decline` and `cancel` carry nothing.
     */
    suspend fun answerInteraction(
        token: String,
        interactionId: String,
        action: String,
        content: JsonObject? = null,
    ): InteractionDto =
        client.post("$root/interactions/$interactionId/answer") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AnswerInteractionRequest(action = action, content = if (action == "accept") content else null))
        }.decodeOrThrow<InteractionResponse>().data

    /**
     * One question by id — what a tapped notification opens. Separate from [pendingInteractions]
     * because minutes can pass between the push and the tap: by then the question may be answered
     * and gone from the list, and the app still has to show what happened to it.
     */
    suspend fun interaction(token: String, interactionId: String): InteractionDto =
        client.get("$root/interactions/$interactionId") {
            bearerAuth(token)
        }.decodeOrThrow<InteractionResponse>().data

    /**
     * Tells the server where to send this install's notifications. Idempotent and keyed by the push
     * token, so it is safe to call on every launch and on every token refresh.
     */
    suspend fun registerDevice(
        token: String,
        pushToken: String,
        platform: String,
        appVersion: String? = null,
        deviceName: String? = null,
    ): RegisterDeviceResponse =
        client.post("$root/devices") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                RegisterDeviceRequest(
                    token = pushToken,
                    platform = platform,
                    appVersion = appVersion,
                    deviceName = deviceName,
                ),
            )
        }.decodeOrThrow()

    /**
     * Signing out: this phone stops being reachable for the current user's questions. The token
     * travels in the body rather than the path — an FCM registration token is opaque and long, and
     * a URL is only a safe place for it as long as it happens to need no escaping.
     */
    suspend fun unregisterDevice(token: String, pushToken: String) {
        client.delete("$root/devices") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UnregisterDeviceRequest(token = pushToken))
        }
    }

    suspend fun addComment(token: String, taskId: String, body: String): CommentDto =
        client.post("$root/tasks/$taskId/comments") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateCommentRequest(body = body))
        }.decodeOrThrow<CommentResponse>().data

    /** Files attached to a task, in upload order. The task detail already carries these too. */
    suspend fun attachments(token: String, taskId: String): List<AttachmentDto> =
        client.get("$root/tasks/$taskId/attachments") {
            bearerAuth(token)
        }.decodeOrThrow<AttachmentsResponse>().data

    /**
     * Uploads one file as a raw body, with its name in the query.
     *
     * Not multipart on purpose: the server reads the body straight through, so there is no
     * boundary encoding to get wrong and no extra dependency on either side. One call per file
     * also means a failed photo fails alone, which on a phone connection is the common case.
     */
    suspend fun uploadAttachment(
        token: String,
        taskId: String,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): AttachmentDto =
        client.post("$root/tasks/$taskId/attachments?fileName=${fileName.encodeURLParameter()}") {
            bearerAuth(token)
            contentType(mimeType?.let { runCatching { ContentType.parse(it) }.getOrNull() } ?: ContentType.Application.OctetStream)
            setBody(bytes)
        }.decodeOrThrow<AttachmentResponse>().data

    /** The bytes of one attachment — what the thumbnail and the full-screen viewer both render. */
    suspend fun downloadAttachment(token: String, taskId: String, attachmentId: String): ByteArray {
        val response = client.get("$root/tasks/$taskId/attachments/$attachmentId") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, "Не удалось загрузить файл (HTTP ${response.status.value})")
        }
        return response.readRawBytes()
    }

    suspend fun deleteAttachment(token: String, taskId: String, attachmentId: String) {
        val response = client.delete("$root/tasks/$taskId/attachments/$attachmentId") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            val serverMessage = runCatching { response.body<ErrorResponse>().message }.getOrNull()
            throw ApiException(response.status.value, serverMessage ?: "Не удалось удалить файл")
        }
    }

    /**
     * Every worker of the installation with the harness limits it runs under. Not scoped to the
     * caller's projects — a worker belongs to the deployment, and "почему ничего не идёт" cannot be
     * asked from a project's side.
     */
    suspend fun workers(token: String): List<WorkerDto> =
        client.get("$root/workers") {
            bearerAuth(token)
        }.decodeOrThrow<WorkersResponse>().data

    /** The same limits seen from the accounts they belong to, with the workers spending each one. */
    suspend fun harnessSubscriptions(token: String): List<HarnessSubscriptionDto> =
        client.get("$root/subscriptions") {
            bearerAuth(token)
        }.decodeOrThrow<SubscriptionsResponse>().data

    /**
     * One page of the activity feed, newest first. [before] is the `nextBefore` cursor of the
     * previous page; null starts from the top.
     */
    suspend fun activities(token: String, before: String? = null, limit: Int? = null): ActivitiesPageDto =
        client.get("$root/activities") {
            bearerAuth(token)
            url {
                before?.let { parameters.append("before", it) }
                limit?.let { parameters.append("limit", it.toString()) }
            }
        }.decodeOrThrow<ActivitiesResponse>().data

    /**
     * Everything actionable right now (questions, reviews, held diffs) plus the unseen counter —
     * the drawer badge, the app badge and the activities screen's top section, in one request.
     */
    suspend fun activitySummary(token: String): ActivitySummaryDto =
        client.get("$root/activities/summary") {
            bearerAuth(token)
        }.decodeOrThrow<ActivitySummaryResponse>().data

    /** "Ленту видел" — moves the per-user mark the unseen badge counts against. */
    suspend fun markActivitiesSeen(token: String) {
        client.post("$root/activities/seen") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(MarkSeenRequest())
        }.decodeOrThrow<MarkSeenResponse>()
    }

    /** Workspace proposals of the user's projects that need a decision (or hold a directory). */
    suspend fun proposals(token: String, holding: Boolean = false): List<ProposalDto> =
        client.get("$root/proposals") {
            bearerAuth(token)
            if (holding) url { parameters.append("holding", "true") }
        }.decodeOrThrow<ProposalsResponse>().data

    /**
     * Approves one proposal revision: the pinned worker commits and pushes the reviewed diff. A
     * stale revision or a status that moved on comes back as an [ApiException] with HTTP 409 —
     * refetch and let the user look again, the server never applies a decision to something they
     * did not see.
     */
    suspend fun approveProposal(
        token: String,
        proposalId: String,
        revision: Int,
        targetBranch: String? = null,
        commitMessage: String? = null,
    ): ProposalDto =
        client.post("$root/proposals/$proposalId/approve") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ApproveProposalRequest(revision = revision, targetBranch = targetBranch, commitMessage = commitMessage))
        }.decodeOrThrow<ProposalResponse>().data

    /** Rejects one proposal revision: the worker resets the workspace and drops the reservation. */
    suspend fun rejectProposal(token: String, proposalId: String, revision: Int): ProposalDto =
        client.post("$root/proposals/$proposalId/reject") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(RejectProposalRequest(revision = revision))
        }.decodeOrThrow<ProposalResponse>().data

    /** The notification policy, cut down to the caller's projects and their pipelines. */
    suspend fun notificationPolicy(token: String): NotificationPolicyDto =
        client.get("$root/notification-policy") {
            bearerAuth(token)
        }.decodeOrThrow<NotificationPolicyResponse>().data

    /**
     * Replaces `defaults` and the caller's own project/pipeline entries; entries of other owners
     * survive untouched on the server. Last-write-wins between two simultaneous editors.
     */
    suspend fun updateNotificationPolicy(
        token: String,
        defaults: JsonObject? = null,
        projects: JsonObject? = null,
        pipelines: JsonObject? = null,
    ): NotificationPolicyDto =
        client.put("$root/notification-policy") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateNotificationPolicyRequest(defaults = defaults, projects = projects, pipelines = pipelines))
        }.decodeOrThrow<NotificationPolicyResponse>().data

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
