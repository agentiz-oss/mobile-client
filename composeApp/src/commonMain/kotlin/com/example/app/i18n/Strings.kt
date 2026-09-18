package com.example.app.i18n

/**
 * Every word the app writes for itself, in one table per language.
 *
 * ### Why an interface and not a resource file
 *
 * Compose's own `stringResource` is `@Composable`, and a good third of the text here is assembled
 * outside composition — an age, a duration, a byte count, the plural of «окно» — by pure functions
 * that screens *and* tests call directly. An interface answers everywhere, needs no code generation,
 * and hands the completeness check to the compiler: a language whose table is missing an entry does
 * not build, which is a stronger guarantee than any test over a `strings.xml` could give.
 *
 * ### What is *not* here
 *
 * Text the **server** writes: an inbox row's badge, headline, facts and button labels, an activity
 * type's name, a run's summary, an agent's question. That is deliberate and is not an oversight —
 * those words are the server's single source of truth for three surfaces at once (the panel, the
 * phone and the push notification), so a copy of them here would be a fourth spelling that drifts.
 * Until the server learns to answer in the reader's language, those strings stay in the language it
 * speaks, and the app renders them as it receives them.
 *
 * ### Rules for adding to this file
 *
 * - **Whole phrases, never fragments.** A sentence assembled from a noun and a verb at the call site
 *   is a sentence that only works in the language it was written in. That is why counts are methods
 *   ([runsActive], [windowsLeft]) rather than a number the caller concatenates: agreement is the
 *   table's business, and Russian needs three forms where English needs two.
 * - **Name after the meaning, not the words.** [runStateFailed], not `errorWord` — a table is
 *   re-read by somebody who cannot read all three languages in it.
 * - Anything interpolated is a function parameter, so no language is forced into the word order of
 *   the one it was translated from.
 */
interface Strings {

    /** This table's language. Lets a screen name the language it is showing without a lookup. */
    val lang: Lang

    // ---------------------------------------------------------------- shared vocabulary

    /** A network call failed and [detail] is whatever the client library said. */
    fun networkError(detail: String?): String

    /** The same failure with nothing to say about it. */
    val networkErrorShort: String

    /** An attachment upload was refused by the server. */
    fun fileUploadFailed(status: Int): String

    /** Deleting an attachment was refused and the server said nothing useful. */
    val fileDeleteFailed: String

    val retry: String
    val back: String
    val cancel: String
    val close: String
    val loading: String
    val sending: String
    val yes: String
    val no: String
    val other: String

    // ---------------------------------------------------------------- the shell: drawer, footer

    val menu: String
    val openMenu: String
    val settings: String
    val profile: String
    val backAction: String

    /** The drawer's footer line: which build this is. */
    fun version(name: String): String

    val menuProjects: String

    /** The drawer entry leading back up to the project currently open. */
    fun menuTasksOf(projectName: String): String

    val menuRuns: String
    fun menuRunsCount(count: Int): String
    val menuInbox: String
    fun menuInboxCount(count: Int): String
    val menuWorkers: String
    fun menuWorkersCount(count: Int): String
    val menuAgent: String

    /** Stand-in name for a project a push notification named by id only. */
    val projectFallbackName: String

    // ---------------------------------------------------------------- login

    val loginPrompt: String
    val loginServer: String
    val loginName: String
    val loginPassword: String
    val loginSubmit: String
    val loginBusy: String
    fun loginConnectFailed(detail: String?): String
    val passwordShow: String
    val passwordHide: String

    // ---------------------------------------------------------------- projects

    val projectsTitle: String
    val projectsLoading: String
    val projectsEmpty: String
    val projectActive: String
    val projectInactive: String

    // ---------------------------------------------------------------- profile

    val profileTitle: String
    val profileServer: String
    val profileLogout: String

    // ---------------------------------------------------------------- settings hub

    val settingsTitle: String
    val settingsNotifications: String
    val settingsNotificationsHint: String
    val settingsLanguage: String

    /** The language row's subtitle: which language is on, and that it follows the profile. */
    val settingsLanguageHint: String

    val languageTitle: String

    /** Shown under the picker while the choice is being written to the profile. */
    val languageSaving: String

    /** The choice took effect locally but the profile could not be updated. */
    val languageSaveFailed: String

    // ---------------------------------------------------------------- agent dashboard

    val agentTitle: String
    val agentConnecting: String
    val agentOpenedInBrowser: String

    // ---------------------------------------------------------------- tasks

    val tasksNew: String
    val tasksLoading: String
    val tasksEmpty: String
    val taskTitleLabel: String
    val taskTitlePlaceholder: String
    val taskDescriptionLabel: String
    val taskDescriptionPlaceholder: String
    val taskCreate: String
    val taskCreating: String
    val taskFallbackTitle: String
    val taskLoading: String

    /** One task's run counter under its title. */
    fun taskRunCount(count: Int): String

    val taskRunPipeline: String
    val taskRunAgain: String
    val taskRunning: String
    val taskStopRun: String
    val taskStopRequested: String
    val taskDiscussion: String
    val taskNoComments: String
    val taskCommentLabel: String
    val taskCommentPlaceholder: String
    val taskCommentSend: String
    fun taskCommentAttach(count: Int): String
    val taskGoToRun: String
    fun taskRunNumber(number: Int): String

    val authorAgent: String
    val authorSystem: String
    val authorHuman: String

    fun taskState(status: String): String?

    // ---------------------------------------------------------------- attachments

    /** The strip's header inside the description card. */
    fun attachmentCount(count: Int): String

    val attachmentPhoto: String
    val attachmentFile: String
    val attachmentRemove: String
    val attachmentDelete: String
    val attachmentDeleting: String
    val attachmentUnviewable: String

    /** Progress while several files go up one request at a time. */
    fun attachmentUploading(index: Int, total: Int, fileName: String): String

    /** `512 Б` / `1,5 МБ` — units and decimal comma are the language's. */
    fun bytes(count: Long): String

    // ---------------------------------------------------------------- runs board

    val runsTitle: String
    val runsLoading: String
    fun runsRunningNow(count: Int): String
    fun runsActiveHeader(count: Int): String
    val runsIdle: String
    val runsRecent: String
    val runFallbackTitle: String
    val runLoading: String
    val runWaitingAnswer: String
    fun runWaitingAnswerCount(count: Int): String

    /** Title of one run's own page. */
    fun runNumberTitle(number: Int): String

    // ---------------------------------------------------------------- run states

    fun runState(status: String): String?
    fun stageState(status: String): String?

    // ---------------------------------------------------------------- one run's page

    val runFactStatus: String
    val runFactDuration: String
    val runFactTokens: String
    val runInstructionTitle: String
    val runInstructionFromComment: String
    val runInstructionFromTask: String
    val runInstructionCollapse: String
    val runInstructionExpand: String
    val runResultTitle: String
    val runQuestionsTitle: String
    val runStagesTitle: String
    val runWorkerSummaryTitle: String
    val runLogTitle: String
    val runRawResultTitle: String
    val runRawResultLabel: String
    val runStageOutputLabel: String
    val runOpenTask: String
    val runTaskWord: String

    /** Spend breakdown under a finished run. */
    fun tokensTotal(text: String): String
    fun tokensInput(text: String): String
    fun tokensOutput(text: String): String
    fun tokensCache(text: String): String

    /** The badge on a run card: `110k ткн`. */
    fun tokensBadge(text: String): String

    /** The stage chip's own spend, short form. */
    fun tokensShort(text: String): String

    /** How many lines are folded behind the log's header. */
    fun logLines(count: Int): String

    val logLevelAll: String
    fun logLevel(level: String): String?

    // ---------------------------------------------------------------- the diff

    val diffTitle: String

    /** `от a1b2c3 · 2 файл(ов), ` — the head of the diff's summary line. */
    fun diffFrom(sha: String, files: Int): String

    fun diffApplied(at: String, commit: String): String
    val diffNotPushed: String
    val diffTruncated: String
    val diffEmpty: String
    val diffBinary: String
    val diffNoLines: String

    // ---------------------------------------------------------------- inbox

    val inboxTitle: String
    fun inboxNeedAction(count: Int): String
    val inboxEmpty: String
    val inboxTabActionable: String
    val inboxTabFeed: String
    val inboxSwipeNotifications: String
    val inboxSwipeDismiss: String
    val inboxHiddenShown: String
    fun inboxHiddenCount(count: Int): String
    val inboxHiddenMark: String
    val inboxAlreadyDecided: String
    val actionAlreadyDecided: String

    /** «Требуется ваше участие» over a task's or a run's own block. */
    val actionRequiredTitle: String
    fun actionRequiredTitleCount(count: Int): String

    fun waitingFor(age: String): String
    fun answerAwaitedFor(left: String): String

    /** The row's own notification line, with the tap target spelled out. */
    fun notifyConfigure(label: String): String

    // ---------------------------------------------------------------- activity feed

    val activitiesLoading: String
    val activitiesEmpty: String
    val activitiesLoadingMore: String
    val activitiesShowMore: String

    // ---------------------------------------------------------------- agent questions

    val interactionWaiting: String
    val interactionNoFields: String
    fun interactionDeadline(at: String, left: String): String
    fun interactionMissing(fields: String): String
    val interactionAnswer: String
    val interactionSkip: String
    val interactionCancel: String
    val interactionOwnOption: String
    val interactionOwnOptionLabel: String
    val interactionAnswerPlaceholder: String
    val interactionNumberPlaceholder: String
    fun interactionClosed(state: String): String
    fun interactionState(status: String, result: String?, who: String?): String

    // ---------------------------------------------------------------- launch options

    val runOptionsTitle: String
    fun runOptionsSummary(summary: String): String
    val runOptionsHint: String
    val runOptionsHarness: String
    val runOptionsModel: String
    val runOptionsLevel: String
    val runOptionsDefault: String
    val runOptionsPipelineHarness: String
    val runOptionsPipelineModel: String
    val runOptionsCliLevel: String
    fun runOptionsLevelSummary(title: String): String
    fun runOptionsAppliesToStages(count: Int): String
    val runOptionsAsPipeline: String
    fun runOptionsAsPipelineNamed(value: String): String

    // ---------------------------------------------------------------- workspace proposal

    val proposalTitleWaiting: String
    val proposalTitlePushFailed: String
    val proposalTitleResetFailed: String
    fun proposalRevision(number: Int): String
    fun proposalFileStats(files: Int, insertions: Int, deletions: Int): String
    fun proposalOperations(count: Int): String
    fun proposalBranch(branch: String): String
    val proposalCommitMessage: String
    val proposalBranchLabel: String
    val proposalPush: String
    val proposalApprove: String
    val proposalReject: String
    val proposalRejectRetry: String
    val proposalRejectConfirm: String
    val proposalRejectSubmit: String

    // ---------------------------------------------------------------- approval request

    val approvalTitleFallback: String
    val approvalApproved: String
    val approvalRejected: String
    val approvalCancelled: String
    val approvalDecided: String
    fun approvalVerdict(passed: Boolean): String
    fun approvalBranch(branch: String): String
    fun approvalCommit(sha: String): String
    val approvalApproveExplain: String
    val approvalCommentLabel: String
    val approvalApproveSubmit: String
    val approvalRejectExplain: String
    val approvalRemarksLabel: String
    val approvalRemarksPlaceholder: String
    val approvalRejectSubmit: String
    val approvalApprove: String
    val approvalReject: String

    // ---------------------------------------------------------------- notification settings

    val notificationsTitle: String
    val notificationsSaving: String
    val notificationsLoading: String
    val notificationsEnvPinned: String
    val notificationsEnvPinnedShort: String
    val notificationsDeliveryOnly: String
    val notificationsDeliveryOnlyShort: String
    val notificationsGeneralRules: String
    val notificationsAllProjects: String
    val notificationsProjects: String
    val notificationsNoProjects: String
    val notificationsMuteProject: String
    val notificationsMuteExcept: String
    val notificationsAllSettings: String
    fun notificationsRowThisType(projectName: String): String
    val notificationsRowThisTypeHint: String
    val notificationsRowPipeline: String
    val notificationsRowPipelineHint: String
    fun notificationsCurrent(line: String): String
    val projectUnnamed: String

    val channelOn: String
    val channelSilent: String
    val channelOff: String
    val channelInherit: String
    val channelSend: String
    val channelSendSilently: String
    val channelDoNotSend: String

    val scopeMuteAll: String
    val scopeMuteProject: String
    val scopeDefault: String
    val scopeInherit: String
    val scopeEnabled: String
    val scopeDisabled: String
    val scopeOn: String
    val scopeOff: String

    fun scopeSummaryMutedExcept(muteLabel: String, rules: Int): String
    fun scopeSummaryRules(rules: Int): String

    // ---------------------------------------------------------------- android push channels

    val pushChannelQuestions: String
    val pushChannelQuestionsHint: String
    val pushChannelActionable: String
    val pushChannelActionableHint: String
    val pushChannelFailures: String
    val pushChannelFailuresHint: String
    val pushChannelResults: String
    val pushChannelResultsHint: String

    // ---------------------------------------------------------------- workers

    val workersTitle: String
    val workersTabWorkers: String
    val workersTabSubscriptions: String
    val workersLoading: String
    fun workersNeedLogin(count: Int): String
    fun workersSubscriptionsExhausted(count: Int): String
    val workersNoneRegistered: String
    val workersNoneAvailable: String
    val workersNoSubscriptions: String
    val workerNeedsLogin: String
    fun workerMaxJobs(count: Int): String
    fun workerLastSeen(at: String): String
    val workerNoHarnesses: String
    fun workerSubscription(name: String): String
    val workerSubscriptionUnbound: String
    fun workerRunningJobs(count: Int): String
    fun workerQueuedJobs(count: Int): String
    fun workerMaxConcurrent(count: Int): String
    val workerAccountMismatch: String
    fun workerReportedAt(at: String): String
    fun subscriptionWorkers(count: Int): String
    val subscriptionNoWorkers: String
    val subscriptionNoTelemetry: String
    fun subscriptionDataAt(at: String): String
    fun subscriptionRemainingPercent(percent: Int): String
    fun subscriptionUsedPercent(percent: Int): String
    val subscriptionNoData: String
    fun subscriptionResetAt(at: String, left: String): String
    fun subscriptionResetLeft(until: String, sessions: String): String

    /** The tail of that bracket when whole session windows still fit: `, ещё 29 полных …`. */
    fun subscriptionAlsoSessions(sessions: String): String
    fun subscriptionIdle(age: String): String
    val subscriptionJustNow: String
    fun workersShowInactive(count: Int): String
    fun workersHideInactive(count: Int): String
    fun workerLimitUntil(at: String, left: String): String
    val workerLimitClosed: String
    fun workerAuthEndedAt(at: String): String
    val workerAuthEnded: String
    val workerAuthFixClaude: String
    val workerAuthFixOther: String
    val workerAuthQueueNote: String

    fun subscriptionState(status: String): String?
    fun workerContactState(state: String): String
    fun workerStatusWord(status: String): String?

    /** `осталось 45 мин` in a bracket beside a reset moment. */
    fun remainingSuffix(left: String): String

    // ---------------------------------------------------------------- json viewer

    fun jsonFields(count: Int): String
    fun jsonItems(count: Int): String
    fun jsonShowMore(count: Int): String

    // ---------------------------------------------------------------- time

    /** `05.08.2026` in the language's own order and separators. */
    fun date(year: Int, month: Int, day: Int): String

    /** `1 ч 12 мин` / `5 мин` / `<1 мин` — how long something took. */
    fun duration(hours: Long, minutes: Long): String

    /** `2 ч 15 мин` / `45 мин` — how long until something happens. */
    fun remaining(hours: Long, minutes: Long): String

    /** `15 мин` / `2 ч` / `3 дн` — how long something has been waiting, one unit only. */
    fun age(minutes: Long, hours: Long, days: Long): String

    /**
     * `29 полных 5-часовых окон` — whole session windows still fitting before a long window resets.
     * [windowMinutes] is part of the phrase, because a plan whose session is not five hours long has
     * to read correctly too.
     */
    fun windowsLeft(count: Long, windowMinutes: Long): String

    /** Files a picker offers to filter by, named in the OS's own dialog. */
    val filePickerImages: String
}
