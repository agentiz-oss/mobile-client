package com.example.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    /** IANA name from the admin profile; display-only, the offset below is what gets applied. */
    val timezone: String? = null,
    /**
     * The profile timezone's offset from UTC in minutes, computed by the server at login/restore.
     * The app has no timezone database, so every timestamp it renders is UTC plus this number.
     */
    val utcOffsetMinutes: Int? = null,
)

/** Response of GET /auth/me. */
@Serializable
data class MeResponse(val user: UserDto)

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

/** A task as the list screen renders it. */
@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val status: String = "new",
    val priority: String = "normal",
    val tags: List<String> = emptyList(),
    val externalId: String = "",
    val createdAt: String? = null,
    val description: String? = null,
    val runCount: Int = 0,
)

/** One stage of a pipeline run, in execution order. */
@Serializable
data class StageDto(
    val role: String,
    val status: String,
    val summary: String? = null,
    val output: JsonElement? = null,
    val errorMessage: String? = null,
)

/**
 * One line of the run's process trace: `debug` is the worker/harness's step-by-step "thinking",
 * `info` marks milestones, `warn`/`error` as usual. `stageRole` is null for run-level lines.
 */
@Serializable
data class LogEntryDto(
    val level: String = "info",
    val message: String = "",
    val stageRole: String? = null,
    val createdAt: String? = null,
)

/** Counters of a stored diff, as the server computed them at write time. */
@Serializable
data class DiffStatsDto(
    val files: Int = 0,
    val insertions: Int = 0,
    val deletions: Int = 0,
)

/**
 * What the run changed: a unified git patch plus the metadata the «Изменения» section renders.
 * The server resolves the dashboard's `proposal ? latestDiff : diff` choice before answering, so
 * this is always the revision a reviewer would look at.
 */
@Serializable
data class DiffDto(
    /** Unified git patch — what UnifiedPatchParser consumes. Null only for ops-only diffs. */
    val patch: String? = null,
    /** The patch was cut at the server's size cap — warn, but parse what arrived anyway. */
    val truncated: Boolean = false,
    val stats: DiffStatsDto? = null,
    val baseSha: String? = null,
    /** Null while the change is still held in Agentiz; set once it reached the repository. */
    val appliedAt: String? = null,
    val appliedCommitSha: String? = null,
)

/**
 * Token spend as the server reports it: for a run accumulated across every attempt, for a stage
 * the last attempt (the same shape sits in `stage.output.usage`). `null` — and absent against an
 * older server or in a run cached by an older build — means "never reported" (old runs, runs that
 * failed before an agent ran) and must render as nothing, not as zero.
 */
@Serializable
data class RunUsageDto(
    val totalTokens: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    /** litellm's pricing estimate; on a subscription the real marginal cost is zero. */
    val estimatedCostUsd: Double? = null,
    /** Only in the per-stage block: which model the stage actually ran on. */
    val model: String? = null,
)

/** The most recent pipeline run of a task: what it concluded and how far it got. */
@Serializable
data class RunDto(
    val id: String,
    val status: String,
    val trigger: String = "manual",
    val resultSummary: String? = null,
    val errorMessage: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val stages: List<StageDto> = emptyList(),
    val logs: List<LogEntryDto> = emptyList(),
    /**
     * Immutable final payload accepted from the worker.  It can contain stage outputs, changed
     * files and worker-specific diagnostic data, so keep it as JSON rather than losing fields in
     * a prematurely narrow client DTO.
     */
    val workerResult: JsonElement? = null,
    /**
     * Questions the agent asked during this run — `pending` ones are what the run is blocked on,
     * the rest are the record of what was asked and how it was answered.
     */
    val interactions: List<InteractionDto> = emptyList(),
    /**
     * Where the run belongs. Filled in by the cross-project board (`GET /runs`) and by a single
     * run's own endpoint, which is reached from the board, the feed and notifications with nothing
     * but two ids in hand and has to be able to name — and open — the task behind them. Null in
     * the copy embedded in a task's detail, where the screen already knows, and against a server
     * that predates this.
     */
    val taskId: String? = null,
    val taskTitle: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val createdAt: String? = null,
    /** How many questions this run is currently parked on; board rows only. */
    val pendingInteractions: Int = 0,
    /** Newest log line of a live run — what a board row shows instead of the whole trace. */
    val lastLog: LogEntryDto? = null,
    /**
     * What this run changed in code, when it changed anything. Defaulted so a cached run saved
     * before this field existed — and an older server that never sends it — both deserialize.
     */
    val diff: DiffDto? = null,
    /** What this run cost in tokens. Defaulted for the same reason as [diff]. */
    val usage: RunUsageDto? = null,
    /**
     * What the run was asked to do — the comment it was triggered from, or the task's description.
     * The run screen leads with it: a task named "выполни" tells a reader nothing, and the agent's
     * answer is unreadable without the question. Null on an older server and on a run whose task
     * carries neither.
     */
    val instruction: RunInstructionDto? = null,
    /**
     * What this run wants from a person, in the same shape the inbox renders. The run screen puts
     * it above everything the run produced: a run that stopped on a question, on a review or on a
     * failure used to say so only through its status word, leaving the reader to work out what the
     * remedy was. Empty on an older server and on a run nobody has to touch.
     */
    val actionRequired: List<InboxItemDto> = emptyList(),
)

/**
 * The instruction behind a run. [source] is `comment` when it came from the comment the run was
 * started from (the one the worker puts last in the prompt as the current instruction) and
 * `description` when the task's own text is all there was.
 */
@Serializable
data class RunInstructionDto(
    val source: String = "description",
    val body: String = "",
    val authorName: String? = null,
    val createdAt: String? = null,
)

/**
 * The run board: everything in flight, plus the runs that finished most recently. Two lists rather
 * than one sorted one because the screen shows them as two sections and "идёт сейчас" is the part
 * the user opened the screen for.
 */
@Serializable
data class RunBoardDto(
    val active: List<RunDto> = emptyList(),
    val recent: List<RunDto> = emptyList(),
)

/**
 * One question an agent asked a person mid-run (ACP form elicitation). The run and its stage sit in
 * `waiting_input` until this is answered, so an unanswered one is not a notification the reader may
 * postpone — it is the thing holding the pipeline up.
 *
 * [requestedSchema] is a JSON Schema object (`type: "object"` with `properties`) describing the form
 * to fill in; the client renders it and the server validates the answer against the very same
 * schema, so a form this app cannot render faithfully is still safe to submit as raw JSON.
 */
@Serializable
data class InteractionDto(
    val id: String,
    val runId: String = "",
    val projectId: String = "",
    val taskId: String? = null,
    val taskTitle: String? = null,
    val projectName: String? = null,
    val stageRole: String? = null,
    val stageIndex: Int? = null,
    val source: String = "acp",
    val message: String = "",
    val requestedSchema: JsonObject = JsonObject(emptyMap()),
    val status: String = "pending",
    val responseAction: String? = null,
    val answeredByName: String? = null,
    val answeredAt: String? = null,
    val expiresAt: String? = null,
    val createdAt: String? = null,
)

/** One-use bridge into the Adminizer Assistant WebView. */
@Serializable
data class AssistantWebviewSessionDto(
    val url: String,
    val expiresAt: String? = null,
)

/**
 * One entry of a task's discussion. `authorKind` is what the UI keys off: `system` for lifecycle
 * events, `human` for people, `agent` for a pipeline run reporting its outcome.
 */
@Serializable
data class CommentDto(
    val id: String,
    val authorKind: String = "human",
    val authorName: String? = null,
    val body: String = "",
    val runId: String? = null,
    val createdAt: String? = null,
)

/** Everything the task screen shows: the task, its latest run, and the thread. */
@Serializable
data class TaskDetailDto(
    val task: TaskDto,
    val latestRun: RunDto? = null,
    val comments: List<CommentDto> = emptyList(),
    /**
     * Unanswered questions across *all* of the task's runs, not just [latestRun] — a question can
     * belong to a run this payload does not carry in full, and it would then be invisible.
     */
    val pendingInteractions: List<InteractionDto> = emptyList(),
    /** Files and photos attached to the task; the agent receives these when a run starts. */
    val attachments: List<AttachmentDto> = emptyList(),
    /**
     * What this task is waiting on from a person, in the same shape the inbox renders — a question,
     * a review, a failed push, a held diff. The task screen states it above everything else, so
     * "что от меня хотят" is not something to deduce from a run's page.
     */
    val actionRequired: List<InboxItemDto> = emptyList(),
)

/**
 * One file attached to a task. Metadata only — the bytes are fetched separately, by id, so a
 * task with a dozen photos costs one small payload to open and downloads only what is on screen.
 */
@Serializable
data class AttachmentDto(
    val id: String,
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val uploadedByName: String? = null,
    val createdAt: String? = null,
) {
    /** Whether the app can render this inline as a thumbnail rather than as a file row. */
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
}

@Serializable
data class AttachmentsResponse(val data: List<AttachmentDto> = emptyList())

@Serializable
data class AttachmentResponse(val data: AttachmentDto)

/**
 * What a manual launch may choose for this task, from GET /tasks/{id}/run-options.
 *
 * The server resolves [defaults] exactly where the job snapshot resolves them (launch choice →
 * `spec.stages[].model` → the role's model), so the line the screen shows is what actually runs
 * when nothing is picked.
 */
@Serializable
data class RunOptionsDto(
    val defaults: RunDefaultsDto = RunDefaultsDto(),
    val stages: List<RunStageOptionDto> = emptyList(),
    val executors: List<RunExecutorOptionDto> = emptyList(),
    val harnesses: List<HarnessProfileDto> = emptyList(),
    val reasoningLevels: List<ReasoningLevelDto> = emptyList(),
)

@Serializable
data class RunDefaultsDto(
    val harnessKey: String? = null,
    val harnessTitle: String? = null,
    val model: String? = null,
)

@Serializable
data class RunStageOptionDto(
    val order: Int = 0,
    val role: String = "",
    val harnessKey: String? = null,
    val harnessTitle: String? = null,
    val model: String? = null,
)

@Serializable
data class RunExecutorOptionDto(
    val workerId: String,
    val executorKey: String,
    val title: String = "",
    val workerName: String = "",
    val harnessKey: String? = null,
)

/** Suggested models and thinking levels of one harness. A model outside the list is still legal. */
@Serializable
data class HarnessProfileDto(
    val key: String,
    val title: String = "",
    val models: List<HarnessModelDto> = emptyList(),
    val reasoningLevels: List<String> = emptyList(),
)

@Serializable
data class HarnessModelDto(val id: String, val title: String = "")

@Serializable
data class ReasoningLevelDto(val value: String, val title: String = "")

@Serializable
data class RunOptionsResponse(val data: RunOptionsDto)

/**
 * The three choices a launch carries. All null = run the pipeline exactly as configured, which is
 * what the button did before the pickers existed.
 */
@Serializable
data class RunTaskRequest(
    val workerId: String? = null,
    val executorKey: String? = null,
    val model: String? = null,
    val reasoningLevel: String? = null,
)

/** Reference to a queued run, returned by POST /tasks/{id}/run. */
@Serializable
data class RunRefDto(
    val id: String,
    val status: String,
)

@Serializable
data class TasksResponse(val data: List<TaskDto> = emptyList())

@Serializable
data class TaskResponse(val data: TaskDto)

/** Body of POST /tasks/{id}/status — only the statuses a person sets by hand are accepted. */
@Serializable
data class TaskStatusRequest(val status: String)

/** Answer of POST /tasks/{taskId}/runs/{runId}/apply. */
@Serializable
data class ApplyDiffResponse(val data: JsonObject? = null)

@Serializable
data class TaskDetailResponse(val data: TaskDetailDto)

@Serializable
data class CommentResponse(val data: CommentDto)

@Serializable
data class RunRefResponse(val data: RunRefDto)

@Serializable
data class RunsResponse(val data: List<RunDto> = emptyList())

@Serializable
data class RunResponse(val data: RunDto)

@Serializable
data class RunBoardResponse(val data: RunBoardDto = RunBoardDto())

@Serializable
data class InteractionsResponse(val data: List<InteractionDto> = emptyList())

@Serializable
data class InteractionResponse(val data: InteractionDto)

/**
 * Body of POST /interactions/{id}/answer. `content` is the filled-in form and belongs to `accept`
 * alone — the server rejects a decline or cancel that carries one.
 */
@Serializable
data class AnswerInteractionRequest(
    val action: String,
    val content: JsonObject? = null,
)

/**
 * Body of POST /devices — where to send this install's notifications. Both platforms register a
 * Firebase token, so the token alone says everything about how to reach the device.
 */
@Serializable
data class RegisterDeviceRequest(
    val token: String,
    val platform: String,
    val appVersion: String? = null,
    val deviceName: String? = null,
)

/** Body of DELETE /devices. */
@Serializable
data class UnregisterDeviceRequest(val token: String)

/**
 * `pushEnabled` is about the *server*, not this device: it says whether the deployment has FCM/APNs
 * credentials at all. Without them the token is still stored, so enabling push later needs no new
 * app release.
 */
@Serializable
data class RegisterDeviceResponse(val pushEnabled: Boolean = false)

/** Body of POST /projects/{id}/tasks. */
@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
)

/** Body of POST /tasks/{id}/comments. */
@Serializable
data class CreateCommentRequest(
    val body: String,
)

/**
 * One limit window of a harness subscription — a 5-hour or weekly bucket for a Claude/Codex plan,
 * whatever the provider happens to call it. Everything is optional because the shape is *abstract*:
 * the server passes through whatever the provider reported, and a window with no `usedPercent` is
 * one whose provider gives a reset time but no number, not an error.
 */
@Serializable
data class HarnessWindowDto(
    val key: String = "",
    val label: String? = null,
    val usedPercent: Double? = null,
    val resetsAt: String? = null,
    val observedAt: String? = null,
    val source: String? = null,
)

/** The account a limit actually belongs to, as shown on a worker's harness row. */
@Serializable
data class HarnessSubscriptionRefDto(
    val id: String,
    val name: String,
    val provider: String? = null,
    val authKind: String? = null,
    val exhausted: Boolean = false,
    val exhaustedUntil: String? = null,
    val exhaustedReason: String? = null,
)

/**
 * One harness available on one worker. [state] is decided by the server (`available`, `exhausted`,
 * `disabled`) and is the only thing the UI colours by — "исчерпан" is a gate the server closed, not
 * something re-derived here out of the percentages.
 */
@Serializable
data class WorkerHarnessDto(
    val id: String,
    val harnessKey: String,
    val enabled: Boolean = true,
    val state: String = "available",
    val maxConcurrent: Int? = null,
    val runningJobs: Int = 0,
    val queuedJobs: Int = 0,
    val accountMismatch: Boolean = false,
    val observedAt: String? = null,
    val subscription: HarnessSubscriptionRefDto? = null,
    val windows: List<HarnessWindowDto> = emptyList(),
)

/** A worker machine with the harnesses bound to it. */
@Serializable
data class WorkerDto(
    val id: String,
    val name: String,
    val status: String = "active",
    val contactState: String = "never_contacted",
    val lastSeenAt: String? = null,
    val version: String? = null,
    val hostname: String? = null,
    val maxConcurrentJobs: Int = 1,
    val timezone: String? = null,
    val harnesses: List<WorkerHarnessDto> = emptyList(),
)

@Serializable
data class WorkersResponse(val data: List<WorkerDto> = emptyList())

/** A worker seen from the subscription it spends — the reverse of [WorkerHarnessDto]. */
@Serializable
data class SubscriptionWorkerDto(
    val id: String,
    val name: String,
    val harnessKey: String = "",
    val enabled: Boolean = true,
    val contactState: String = "never_contacted",
    val runningJobs: Int = 0,
)

/**
 * A provider account with its limits. This is what actually runs out — two workers signed into one
 * account exhaust together — which is why the app shows it as its own tab and not only as a line on
 * each worker.
 */
@Serializable
data class HarnessSubscriptionDto(
    val id: String,
    val name: String,
    val provider: String? = null,
    val authKind: String? = null,
    val notes: String? = null,
    val accountId: String? = null,
    val exhausted: Boolean = false,
    val exhaustedUntil: String? = null,
    val exhaustedReason: String? = null,
    val lastSignalAt: String? = null,
    val lastSignalSource: String? = null,
    val windows: List<HarnessWindowDto> = emptyList(),
    val workers: List<SubscriptionWorkerDto> = emptyList(),
)

@Serializable
data class SubscriptionsResponse(val data: List<HarnessSubscriptionDto> = emptyList())

/**
 * One row of the activity feed — the immutable "что произошло" journal. The server writes it for
 * every event whatever the notification policy silences, so this list is also the answer to
 * "почему не пришёл пуш".
 */
@Serializable
data class ActivityDto(
    val id: String,
    val type: String,
    /** `action_required` or `info` — what the row is coloured by. */
    val kind: String = "info",
    val projectId: String = "",
    val projectName: String? = null,
    val runId: String? = null,
    val taskId: String? = null,
    val taskTitle: String? = null,
    val proposalId: String? = null,
    val interactionId: String? = null,
    val title: String = "",
    val body: String = "",
    val data: JsonObject? = null,
    val createdAt: String? = null,
)

/** One feed page. [nextBefore] is the opaque cursor of the next (older) page, or null at the end. */
@Serializable
data class ActivitiesPageDto(
    val items: List<ActivityDto> = emptyList(),
    val nextBefore: String? = null,
)

@Serializable
data class ActivitiesResponse(val data: ActivitiesPageDto = ActivitiesPageDto())

/** A pending question, as the summary endpoint compresses it. */
@Serializable
data class SummaryInteractionDto(
    val id: String,
    val runId: String = "",
    val projectId: String = "",
    val taskId: String? = null,
    val taskTitle: String? = null,
    val message: String = "",
    val createdAt: String? = null,
    val expiresAt: String? = null,
)

/** A proposal somebody has to approve/reject/retry, as the summary endpoint compresses it. */
@Serializable
data class SummaryProposalDto(
    val id: String,
    val status: String = "waiting_review",
    val revision: Int = 1,
    val projectId: String = "",
    val taskId: String = "",
    val taskTitle: String? = null,
    val runId: String = "",
    val targetBranch: String? = null,
    val commitMessage: String? = null,
    val lastError: String? = null,
    val updatedAt: String? = null,
)

/** A repository run whose diff `requireApproval` holds in Agentiz. */
@Serializable
data class SummaryHeldRunDto(
    val runId: String,
    val projectId: String = "",
    val taskId: String = "",
    val taskTitle: String? = null,
    val diffId: String? = null,
    val operations: Int = 0,
    val finishedAt: String? = null,
)

/**
 * Everything actionable right now plus the unseen-feed counter — one request instead of four
 * polls, and [actionableCount] is the number the app badge shows.
 */
@Serializable
data class ActivitySummaryDto(
    /**
     * Everything waiting on a person, in one shape and one order — the list the inbox renders.
     * Empty against a server that predates it, which is why the three arrays below are still read:
     * they are the same facts in the shape older builds parse.
     */
    val items: List<InboxItemDto> = emptyList(),
    val interactions: List<SummaryInteractionDto> = emptyList(),
    val proposals: List<SummaryProposalDto> = emptyList(),
    val heldRuns: List<SummaryHeldRunDto> = emptyList(),
    val actionableCount: Int = 0,
    val unseen: Int = 0,
)

/**
 * One thing that needs a person. The server decides what it is called ([badge]), what it says
 * ([headline] — what is being asked, [facts] — what to decide on) and what may be done to it
 * ([actions]); the client only draws it and calls the endpoint the action names. Kinds are open on
 * purpose: an unknown one still renders as a card that opens its run.
 */
@Serializable
data class InboxItemDto(
    val id: String,
    val kind: String = "",
    /** The catalogue type behind the kind — `interaction.created`, `proposal.waiting_review`, … */
    val activityType: String = "",
    val badge: String = "",
    val headline: String = "",
    val facts: String? = null,
    /**
     * What is going on and what each button will do about it, written by the server. The list is
     * unreadable without it for anything but a question: "ревью, 0 файлов, кнопка Отклонить" states
     * a state machine, not a choice a person can make.
     */
    val explain: String? = null,
    val projectId: String = "",
    val projectName: String? = null,
    val taskId: String? = null,
    val taskTitle: String? = null,
    val runId: String? = null,
    val interactionId: String? = null,
    val proposalId: String? = null,
    val revision: Int? = null,
    val url: String? = null,
    val waitingSince: String? = null,
    val expiresAt: String? = null,
    val priority: Int = 0,
    val actions: List<InboxActionDto> = emptyList(),
)

/** [key] is what the client dispatches on; [label] is the caption, spelled once on the server. */
@Serializable
data class InboxActionDto(
    val key: String,
    val label: String,
    val style: String = "default",
    /**
     * The argument the endpoint takes when one key means different things on different rows —
     * today only `close_task`, which is `done` after a pull request and `cancelled` after a run
     * that will not be retried.
     */
    val value: String? = null,
)

@Serializable
data class ActivitySummaryResponse(val data: ActivitySummaryDto = ActivitySummaryDto())

/** Body of POST /activities/seen; an omitted [at] means "now". */
@Serializable
data class MarkSeenRequest(val at: String? = null)

@Serializable
data class MarkSeenResponse(val data: JsonObject? = null)

/** A workspace proposal in full, as GET /proposals returns it. */
@Serializable
data class ProposalDto(
    val id: String,
    val status: String = "waiting_review",
    val revision: Int = 1,
    val projectId: String = "",
    val projectName: String? = null,
    val taskId: String = "",
    val taskTitle: String? = null,
    val runId: String = "",
    val runStatus: String? = null,
    val holding: Boolean = false,
    val targetMode: String = "current",
    val targetBranch: String? = null,
    val commitMessage: String? = null,
    /** Whether approve is possible at all for this revision — the server's verdict, not ours. */
    val approvable: Boolean = false,
    val diff: ProposalDiffDto? = null,
    val lastError: String? = null,
    val pushedCommitSha: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class ProposalDiffDto(
    val id: String,
    val operations: Int = 0,
    val stats: DiffStatsDto? = null,
    val truncated: Boolean = false,
)

@Serializable
data class ProposalsResponse(val data: List<ProposalDto> = emptyList())

@Serializable
data class ProposalResponse(val data: ProposalDto)

/** Body of POST /proposals/{id}/approve. Edits are optional; the stored ones apply when omitted. */
@Serializable
data class ApproveProposalRequest(
    val revision: Int,
    val targetBranch: String? = null,
    val commitMessage: String? = null,
)

/** Body of POST /proposals/{id}/reject. */
@Serializable
data class RejectProposalRequest(val revision: Int)

/** One event type of the notification-policy matrix, as the server catalogues it. */
@Serializable
data class ActivityTypeInfoDto(
    val type: String,
    val kind: String = "info",
    val label: String = "",
)

/** The built-in delivery default of one type — the tail of every policy resolution. */
@Serializable
data class PolicyChannelsDto(
    val push: String = "on",
    val dashboard: String = "on",
)

/**
 * The notification policy as this owner sees it: `defaults` and the caller's own project/pipeline
 * scopes. Scopes stay [JsonObject]s on purpose — a scope mixes `mute: true` with per-type entries,
 * and the editor manipulates it structurally (see NotificationPolicyDoc) rather than through a
 * rigid DTO that would drop unknown keys on the way back.
 */
@Serializable
data class NotificationPolicyDto(
    val defaults: JsonObject = JsonObject(emptyMap()),
    val projects: JsonObject = JsonObject(emptyMap()),
    val pipelines: JsonObject = JsonObject(emptyMap()),
    /** `environment` | `settings` | `unset` — env shadows the stored document entirely. */
    val source: String = "unset",
    val shadowedByEnvironment: Boolean = false,
    val builtinDefaults: Map<String, PolicyChannelsDto> = emptyMap(),
    val types: List<ActivityTypeInfoDto> = emptyList(),
)

@Serializable
data class NotificationPolicyResponse(val data: NotificationPolicyDto = NotificationPolicyDto())

/** Body of PUT /notification-policy: replaces `defaults` and the caller's own entries. */
@Serializable
data class UpdateNotificationPolicyRequest(
    val defaults: JsonObject? = null,
    val projects: JsonObject? = null,
    val pipelines: JsonObject? = null,
)

/** Error envelope every endpoint uses on failure: `{ "message": "..." }`. */
@Serializable
data class ErrorResponse(
    val message: String? = null,
)
