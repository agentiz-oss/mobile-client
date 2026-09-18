package com.example.app.i18n

/**
 * English.
 *
 * Translated from [StringsRu], which is where every one of these strings was first written, so the
 * meaning to preserve is the Russian one — including the deliberately blunt ones. «Требуют действия»
 * is "Needs you", not "Action required": the word on the screen is what a person reads before
 * deciding whether to stop what they are doing, and the shorter form is the one that reads as an
 * appointment rather than as a form.
 *
 * Two conventions this table keeps to:
 * - **24-hour time, month name in dates** (`Aug 5, 2026 17:32`). A numeric `08/05` is read as the
 *   fifth of August on one side of the Atlantic and as the eighth of May on the other, and a run's
 *   timestamp is exactly the kind of thing somebody compares against a log.
 * - **The vocabulary is Agentiz's own**, untranslated where the product does not translate it:
 *   worker, harness, pipeline, run, workspace. These are the words the server logs, the panel shows
 *   and the CLI prints; inventing English synonyms would give a reader two names for one thing.
 */
internal object StringsEn : Strings {

    override val lang = Lang.En

    override fun networkError(detail: String?) = "Network error: ${detail ?: "unknown error"}"
    override val networkErrorShort = "Network error"

    override fun fileUploadFailed(status: Int) = "The file could not be uploaded (HTTP $status)"
    override val fileDeleteFailed = "The file could not be deleted"

    override val retry = "Retry"
    override val back = "Back"
    override val cancel = "Cancel"
    override val close = "Close"
    override val loading = "Loading…"
    override val sending = "Sending…"
    override val yes = "Yes"
    override val no = "No"
    override val other = "Other"

    override val menu = "Menu"
    override val openMenu = "Open menu"
    override val settings = "Settings"
    override val profile = "Profile"
    override val backAction = "Back"

    override fun version(name: String) = "Version $name"

    override val menuProjects = "Projects"
    override fun menuTasksOf(projectName: String) = "Tasks: $projectName"
    override val menuRuns = "Runs"
    override fun menuRunsCount(count: Int) = "Runs ($count)"
    override val menuInbox = "Inbox"
    override fun menuInboxCount(count: Int) = "Inbox ($count)"
    override val menuWorkers = "Workers"
    override fun menuWorkersCount(count: Int) = "Workers ($count)"
    override val menuAgent = "Agent"

    override val projectFallbackName = "Project"

    override val loginPrompt = "Sign in to see your projects"
    override val loginServer = "Server"
    override val loginName = "Login"
    override val loginPassword = "Password"
    override val loginSubmit = "Sign in"
    override val loginBusy = "Signing in…"
    override fun loginConnectFailed(detail: String?) =
        "Could not reach the server: ${detail ?: "unknown error"}"

    override val passwordShow = "Show password"
    override val passwordHide = "Hide password"

    override val projectsTitle = "Projects"
    override val projectsLoading = "Loading projects…"
    override val projectsEmpty = "You have no projects yet."
    override val projectActive = "active"
    override val projectInactive = "off"

    override val profileTitle = "Profile"
    override val profileServer = "Server"
    override val profileLogout = "Sign out"

    override val settingsTitle = "Settings"
    override val settingsNotifications = "Notifications"
    override val settingsNotificationsHint =
        "Push and the bell: general rules, and per project"
    override val settingsLanguage = "Language"
    override val settingsLanguageHint = "The app's language. Saved to your profile — the panel reads the same one."
    override val languageTitle = "App language"
    override val languageSaving = "Saving to your profile…"
    override val languageSaveFailed = "Switched here, but your profile was not updated — try again."

    override val agentTitle = "Agent chat"
    override val agentConnecting = "Connecting the agent…"
    override val agentOpenedInBrowser = "The agent opened in your browser"

    override val tasksNew = "New task"
    override val tasksLoading = "Loading tasks…"
    override val tasksEmpty = "This project has no tasks yet."
    override val taskTitleLabel = "Title"
    override val taskTitlePlaceholder = "What needs doing"
    override val taskDescriptionLabel = "Description"
    override val taskDescriptionPlaceholder = "Details (optional)"
    override val taskCreate = "Create"
    override val taskCreating = "Creating…"
    override val taskFallbackTitle = "Task"
    override val taskLoading = "Loading the task…"

    override fun taskRunCount(count: Int) = "$count ${enPlural(count, "run", "runs")}"

    override val taskRunPipeline = "Run the pipeline"
    override val taskRunAgain = "Run again"
    override val taskRunning = "Running…"
    override val taskStopRun = "Stop the run"
    override val taskStopRequested = "Stop requested…"
    override val taskDiscussion = "Discussion"
    override val taskNoComments = "No comments yet."
    override val taskCommentLabel = "New comment"
    override val taskCommentPlaceholder = "Write…"
    override val taskCommentSend = "Send"
    override fun taskCommentAttach(count: Int) =
        if (count == 1) "Attach the file" else "Attach the files"

    override val taskGoToRun = "Go to a run"
    override fun taskRunNumber(number: Int) = "Run #$number"

    override val authorAgent = "agent"
    override val authorSystem = "system"
    override val authorHuman = "human"

    override fun taskState(status: String) = when (status) {
        "new" -> "new"
        "queued" -> "queued"
        "running" -> "running"
        "waiting_input" -> "awaiting an answer"
        "waiting_review" -> "in review"
        "done" -> "done"
        "failed" -> "failed"
        "cancelled" -> "cancelled"
        "ignored" -> "skipped"
        else -> null
    }

    override fun attachmentCount(count: Int) = "$count ${enPlural(count, "file", "files")}"
    override val attachmentPhoto = "Photo"
    override val attachmentFile = "File"
    override val attachmentRemove = "Remove"
    override val attachmentDelete = "Delete"
    override val attachmentDeleting = "Deleting…"
    override val attachmentUnviewable =
        "This file cannot be shown in the app, but it is attached to the task."

    override fun attachmentUploading(index: Int, total: Int, fileName: String) =
        "Uploading $index of $total: $fileName"

    override fun bytes(count: Long) = when {
        count < 1024L -> "$count B"
        count < 1024L * 1024L -> "${count / 1024L} KB"
        else -> "${count / (1024L * 1024L)}.${(count * 10L / (1024L * 1024L)) % 10L} MB"
    }

    override val runsTitle = "Runs"
    override val runsLoading = "Loading runs…"
    override fun runsRunningNow(count: Int) = "$count running now"
    override fun runsActiveHeader(count: Int) = "Running now ($count)"
    override val runsIdle = "Nothing is running right now."
    override val runsRecent = "Finished recently"
    override val runFallbackTitle = "Run"
    override val runLoading = "Loading the run…"
    override val runWaitingAnswer = "awaiting an answer"
    override fun runWaitingAnswerCount(count: Int) = "awaiting an answer ($count)"
    override fun runNumberTitle(number: Int) = "Run #$number"

    override fun runState(status: String) = when (status) {
        "pending" -> "queued"
        "running" -> "running"
        "waiting_input" -> "awaiting an answer"
        "succeeded" -> "succeeded"
        "failed" -> "failed"
        "cancelled" -> "cancelled"
        else -> null
    }

    override fun stageState(status: String) = when (status) {
        "pending" -> "waiting"
        "running" -> "running"
        "waiting_input" -> "awaiting an answer"
        "succeeded" -> "done"
        "failed" -> "failed"
        "skipped" -> "skipped"
        else -> null
    }

    override val runFactStatus = "Status"
    override val runFactDuration = "Duration"
    override val runFactTokens = "Tokens"
    override val runInstructionTitle = "What was asked"
    override val runInstructionFromComment = "from a comment"
    override val runInstructionFromTask = "from the task description"
    override val runInstructionCollapse = "Collapse"
    override val runInstructionExpand = "Show in full"
    override val runResultTitle = "Run result"
    override val runQuestionsTitle = "The agent's questions"
    override val runStagesTitle = "Stages"
    override val runWorkerSummaryTitle = "Worker summary"
    override val runLogTitle = "Execution log"
    override val runRawResultTitle = "The worker's full answer"
    override val runRawResultLabel = "result"
    override val runStageOutputLabel = "stage output"
    override val runOpenTask = "Open the task"
    override val runTaskWord = "Task"

    override fun tokensTotal(text: String) = "$text tokens"
    override fun tokensInput(text: String) = "in $text"
    override fun tokensOutput(text: String) = "out $text"
    override fun tokensCache(text: String) = "cache $text"
    override fun tokensBadge(text: String) = "$text tok"
    override fun tokensShort(text: String) = "$text tok"

    override fun logLines(count: Int) = "$count ${enPlural(count, "line", "lines")}"

    override val logLevelAll = "all"
    override fun logLevel(level: String) = when (level) {
        "error" -> "errors"
        "warn" -> "warnings"
        "info" -> "info"
        "debug" -> "debug"
        else -> null
    }

    override val diffTitle = "Changes"
    override fun diffFrom(sha: String, files: Int) =
        "from $sha · $files ${enPlural(files, "file", "files")}, "

    override fun diffApplied(at: String, commit: String) = " · applied $at, commit $commit"
    override val diffNotPushed = " · not pushed to the repository"
    override val diffTruncated = "The patch was cut at the size limit — this is part of the changes."
    override val diffEmpty = "The diff is empty."
    override val diffBinary = "Binary file — contents are not shown"
    override val diffNoLines = "No lines to show"

    override val inboxTitle = "Inbox"
    override fun inboxNeedAction(count: Int) = "$count ${enPlural(count, "needs", "need")} you"
    override val inboxEmpty = "Nothing is waiting on you."
    override val inboxTabActionable = "Needs you"
    override val inboxTabFeed = "Feed"
    override val inboxSwipeNotifications = "Notifications"
    override val inboxSwipeDismiss = "No action needed"
    override val inboxHiddenShown = "Hidden rows shown — back to the list"
    override fun inboxHiddenCount(count: Int) = "Hidden: $count — show"
    override val inboxHiddenMark = "hidden by you"
    override val inboxAlreadyDecided = "Already settled — the list is about to refresh."
    override val actionAlreadyDecided = "Already settled — the screen is about to refresh."

    override val actionRequiredTitle = "Needs you"
    override fun actionRequiredTitleCount(count: Int) = "Needs you ($count)"

    override fun waitingFor(age: String) = "waiting $age"
    override fun answerAwaitedFor(left: String) = "$left left to answer"
    override fun notifyConfigure(label: String) = "$label · change"

    override val activitiesLoading = "Loading activity…"
    override val activitiesEmpty = "Nothing has happened yet."
    override val activitiesLoadingMore = "Loading…"
    override val activitiesShowMore = "Show more"

    override val interactionWaiting = "The agent is waiting for an answer"
    override val interactionNoFields = "A question with no fields — confirm or decline."
    override fun interactionDeadline(at: String, left: String) =
        "An answer is expected by $at" + if (left.isEmpty()) "" else " ($left left)"

    override fun interactionMissing(fields: String) = "Fill in: $fields"
    override val interactionAnswer = "Answer"
    override val interactionSkip = "Skip"
    override val interactionCancel = "Cancel"
    override val interactionOwnOption = "Other"
    override val interactionOwnOptionLabel = "Your own answer"
    override val interactionAnswerPlaceholder = "Answer…"
    override val interactionNumberPlaceholder = "Number"
    override fun interactionClosed(state: String) = "This question is already closed — $state"

    override fun interactionState(status: String, result: String?, who: String?) = when (status) {
        "pending" -> "awaiting an answer"
        "answered" -> when (result) {
            "accept" -> if (who != null) "answered by $who" else "answered"
            "decline" -> "skipped"
            else -> "cancelled"
        }
        "expired" -> "the answer window passed"
        "cancelled" -> "cancelled"
        "orphaned" -> "the run was interrupted"
        else -> status
    }

    override val runOptionsTitle = "What to run it with"
    override fun runOptionsSummary(summary: String) = "Will run: $summary"
    override val runOptionsHint = "Tap to choose"
    override val runOptionsHarness = "Harness"
    override val runOptionsModel = "Model"
    override val runOptionsLevel = "Reasoning level"
    override val runOptionsDefault = "Default"
    override val runOptionsPipelineHarness = "the pipeline's harness"
    override val runOptionsPipelineModel = "the default model"
    override val runOptionsCliLevel = "level: whatever the CLI does"
    override fun runOptionsLevelSummary(title: String) = "level: ${title.lowercase()}"
    override fun runOptionsAppliesToStages(count: Int) =
        "The choice applies to every stage of the run ($count)"

    override val runOptionsAsPipeline = "As in the pipeline"
    override fun runOptionsAsPipelineNamed(value: String) = "As in the pipeline ($value)"

    override val proposalTitleWaiting = "Changes are waiting for review"
    override val proposalTitlePushFailed = "The push failed — decide what happens next"
    override val proposalTitleResetFailed = "The reset failed — you can retry it"
    override fun proposalRevision(number: Int) = "Revision $number"
    override fun proposalFileStats(files: Int, insertions: Int, deletions: Int) =
        "$files ${enPlural(files, "file", "files")}, +$insertions/−$deletions"

    override fun proposalOperations(count: Int) =
        "$count ${enPlural(count, "operation", "operations")}"

    override fun proposalBranch(branch: String) = "branch $branch"
    override val proposalCommitMessage = "Commit message"
    override val proposalBranchLabel = "Branch"
    override val proposalPush = "Commit and push"
    override val proposalApprove = "Approve…"
    override val proposalReject = "Reject…"
    override val proposalRejectRetry = "Retry the reset…"
    override val proposalRejectConfirm =
        "Reject this revision and reset the workspace? Everything this revision did will be" +
            " removed from the worker."
    override val proposalRejectSubmit = "Yes, reject"

    override val approvalTitleFallback = "Accept the work"
    override val approvalApproved = "Already accepted."
    override val approvalRejected = "Already rejected."
    override val approvalCancelled = "Withdrawn: the workflow was cancelled."
    override val approvalDecided = "A decision has already been made."
    override fun approvalVerdict(passed: Boolean) =
        "the agent's verdict: ${if (passed) "ok" else "not ok"}"

    override fun approvalBranch(branch: String) = "branch $branch"
    override fun approvalCommit(sha: String) = "commit $sha"
    override val approvalApproveExplain =
        "Accept the work? The workflow continues down its «accepted» branch. A comment is" +
            " optional — it stays in the decision's history."
    override val approvalCommentLabel = "Comment (optional)"
    override val approvalApproveSubmit = "Yes, accept"
    override val approvalRejectExplain =
        "What is wrong? This text goes to the developer as the brief for another round of work" +
            " on the task."
    override val approvalRemarksLabel = "Remarks"
    override val approvalRemarksPlaceholder = "The footer slides on a narrow screen"
    override val approvalRejectSubmit = "Reject and send back"
    override val approvalApprove = "Accept…"
    override val approvalReject = "Reject…"

    override val notificationsTitle = "Notifications"
    override val notificationsSaving = "Saving…"
    override val notificationsLoading = "Loading the settings…"
    override val notificationsEnvPinned =
        "The policy is pinned by an environment variable on the server — edits made here are" +
            " saved but do nothing until that variable is removed."
    override val notificationsEnvPinnedShort =
        "The policy is pinned by an environment variable on the server — edits are saved but do" +
            " nothing until it is removed."
    override val notificationsDeliveryOnly =
        "The activity feed is always written; what is switched off here is delivery only — push" +
            " to the phone and the bell in the panel."
    override val notificationsDeliveryOnlyShort =
        "This switches off delivery only — push to the phone. The inbox row and the feed entry" +
            " appear either way."
    override val notificationsGeneralRules = "General rules"
    override val notificationsAllProjects = "For every project"
    override val notificationsProjects = "Projects"
    override val notificationsNoProjects = "No projects yet — nothing to configure."
    override val notificationsMuteProject = "Mute everything in this project"
    override val notificationsMuteExcept = "Except the types given their own value below"
    override val notificationsAllSettings = "All notification settings →"
    override fun notificationsRowThisType(projectName: String) =
        "Notifications like this in «$projectName»"

    override val notificationsRowThisTypeHint =
        "This event type only. Nothing else in the project changes."
    override val notificationsRowPipeline = "For this pipeline only"
    override val notificationsRowPipelineHint =
        "Overrides the project's rule — «the project is quiet, but releases speak», say."
    override fun notificationsCurrent(line: String) = "Now: $line"
    override val projectUnnamed = "unnamed"

    override val channelOn = "on"
    override val channelSilent = "silent"
    override val channelOff = "off"
    override val channelInherit = "as in general"
    override val channelSend = "send"
    override val channelSendSilently = "send silently"
    override val channelDoNotSend = "do not send"

    override val scopeMuteAll = "everything muted"
    override val scopeMuteProject = "muted"
    override val scopeDefault = "default"
    override val scopeInherit = "as in general"
    override val scopeEnabled = "on"
    override val scopeDisabled = "muted"
    override val scopeOn = "on"
    override val scopeOff = "off"

    override fun scopeSummaryMutedExcept(muteLabel: String, rules: Int) =
        "$muteLabel, except $rules"

    override fun scopeSummaryRules(rules: Int) =
        "own rules: $rules"

    override val pushChannelQuestions = "Agent questions"
    override val pushChannelQuestionsHint = "An agent stopped and is waiting for an answer"
    override val pushChannelActionable = "Needs you"
    override val pushChannelActionableHint = "Change reviews, held diffs, failed pushes"
    override val pushChannelFailures = "Run failures"
    override val pushChannelFailuresHint = "A run finished with an error"
    override val pushChannelResults = "Results"
    override val pushChannelResultsHint = "Successful runs and other quiet events"

    override val workersTitle = "Workers"
    override val workersTabWorkers = "Workers"
    override val workersTabSubscriptions = "Subscriptions"
    override val workersLoading = "Loading workers…"
    override fun workersNeedLogin(count: Int) =
        "$count ${enPlural(count, "worker", "workers")} signed out"

    override fun workersSubscriptionsExhausted(count: Int) =
        "$count ${enPlural(count, "subscription", "subscriptions")} exhausted"

    override val workersNoneRegistered = "No worker is registered."
    override val workersNoneAvailable = "No worker can take work right now."
    override val workersNoSubscriptions =
        "No subscriptions: limits appear once a worker first reports its harness usage."
    override val workerNeedsLogin = "sign-in needed"
    override fun workerMaxJobs(count: Int) = "up to $count ${enPlural(count, "job", "jobs")}"
    override fun workerLastSeen(at: String) = "seen $at"
    override val workerNoHarnesses = "No harness is bound — limits do not apply."
    override fun workerSubscription(name: String) = "Subscription: $name"
    override val workerSubscriptionUnbound = "No subscription bound — the limit is not tracked."
    override fun workerRunningJobs(count: Int) = "$count running"
    override fun workerQueuedJobs(count: Int) = "$count queued"
    override fun workerMaxConcurrent(count: Int) = "no more than $count"
    override val workerAccountMismatch =
        "The report came from a different account than the subscription names."
    override fun workerReportedAt(at: String) = "reported $at"
    override fun subscriptionWorkers(count: Int) = "Workers ($count)"
    override val subscriptionNoWorkers = "No worker is bound to this subscription."
    override val subscriptionNoTelemetry = "No limit telemetry has arrived yet."
    override fun subscriptionDataAt(at: String) = "Data as of $at"
    override fun subscriptionRemainingPercent(percent: Int) = "$percent% left"
    override fun subscriptionUsedPercent(percent: Int) = "$percent%"
    override val subscriptionNoData = "no data"
    override fun subscriptionResetAt(at: String, left: String) = "Resets $at$left"
    override fun subscriptionResetLeft(until: String, sessions: String) = " ($until left$sessions)"
    override fun subscriptionAlsoSessions(sessions: String) = ", $sessions"
    override fun subscriptionIdle(age: String) = "Limits unchanged for $age"
    override val subscriptionJustNow = "moments"
    override fun workersShowInactive(count: Int) = "Show inactive ($count)"
    override fun workersHideInactive(count: Int) = "Hide inactive ($count)"
    override fun workerLimitUntil(at: String, left: String) = "Limit closed until $at$left"
    override val workerLimitClosed = "Limit closed"
    override fun workerAuthEndedAt(at: String) = "The sign-in ended $at"
    override val workerAuthEnded = "The sign-in ended"
    override val workerAuthFixClaude =
        "Renewing the subscription and signing in again is only possible in a browser on the" +
            " worker's own machine — «claude auth login», as the user it runs as."
    override val workerAuthFixOther =
        "Renewing the subscription and signing in again is only possible in a browser on the" +
            " worker's own machine."
    override val workerAuthQueueNote =
        "Jobs for this harness are queued and resume on their own a couple of minutes after the" +
            " sign-in."

    override fun subscriptionState(status: String) = when (status) {
        "available" -> "available"
        "exhausted" -> "limit exhausted"
        "unauthorized" -> "sign-in needed"
        "disabled" -> "off"
        else -> null
    }

    override fun workerContactState(state: String) = when (state) {
        "online" -> "online"
        "offline" -> "offline"
        else -> "has never been in touch"
    }

    override fun workerStatusWord(status: String) = when (status) {
        "paused" -> "paused"
        "revoked" -> "revoked"
        "pending" -> "not connected"
        else -> null
    }

    override fun remainingSuffix(left: String) = " ($left left)"

    override fun jsonFields(count: Int) = "$count ${enPlural(count, "field", "fields")}"
    override fun jsonItems(count: Int) = "$count ${enPlural(count, "item", "items")}"
    override fun jsonShowMore(count: Int) = "…show $count more"

    override fun date(year: Int, month: Int, day: Int) = "${MONTHS[month - 1]} $day, $year"

    override fun duration(hours: Long, minutes: Long) = when {
        hours > 0L -> "${hours}h ${minutes % 60L}m"
        minutes == 0L -> "<1m"
        else -> "${minutes}m"
    }

    override fun remaining(hours: Long, minutes: Long) =
        if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"

    override fun age(minutes: Long, hours: Long, days: Long) = when {
        days > 0L -> "${days}d"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }

    override fun windowsLeft(count: Long, windowMinutes: Long): String {
        val unit = if (windowMinutes % 60L == 0L) {
            "${windowMinutes / 60L}-hour"
        } else {
            "$windowMinutes-minute"
        }
        return "$count full $unit ${enPlural(count, "window", "windows")}"
    }

    override val filePickerImages = "Images"
}

/**
 * Short month names for [StringsEn.date]. Spelled out rather than taken from a platform formatter:
 * commonMain has none, and this way the date reads the same on all four targets.
 */
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
