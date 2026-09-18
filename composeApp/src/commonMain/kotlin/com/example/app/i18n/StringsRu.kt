package com.example.app.i18n

/**
 * Russian — the language every one of these strings was originally written in, so this table is
 * also the reference the other two are translated from. Where a phrase reads oddly literally, the
 * wording is the one that was already on the screen and is left alone deliberately: changing it
 * would be a UI change hiding inside a translation.
 */
internal object StringsRu : Strings {

    override val lang = Lang.Ru

    override fun networkError(detail: String?) = "Ошибка сети: ${detail ?: "неизвестная ошибка"}"
    override val networkErrorShort = "Ошибка сети"

    override fun fileUploadFailed(status: Int) = "Не удалось загрузить файл (HTTP $status)"
    override val fileDeleteFailed = "Не удалось удалить файл"

    override val retry = "Повторить"
    override val back = "Назад"
    override val cancel = "Отмена"
    override val close = "Закрыть"
    override val loading = "Загрузка…"
    override val sending = "Отправляется…"
    override val yes = "Да"
    override val no = "Нет"
    override val other = "Другое"

    override val menu = "Меню"
    override val openMenu = "Открыть меню"
    override val settings = "Настройки"
    override val profile = "Профиль"
    override val backAction = "Назад"

    override fun version(name: String) = "Версия $name"

    override val menuProjects = "Проекты"
    override fun menuTasksOf(projectName: String) = "Задачи: $projectName"
    override val menuRuns = "Запуски"
    override fun menuRunsCount(count: Int) = "Запуски ($count)"
    override val menuInbox = "Входящие"
    override fun menuInboxCount(count: Int) = "Входящие ($count)"
    override val menuWorkers = "Воркеры"
    override fun menuWorkersCount(count: Int) = "Воркеры ($count)"
    override val menuAgent = "Агент"

    override val projectFallbackName = "Проект"

    override val loginPrompt = "Войдите, чтобы посмотреть свои проекты"
    override val loginServer = "Сервер"
    override val loginName = "Логин"
    override val loginPassword = "Пароль"
    override val loginSubmit = "Войти"
    override val loginBusy = "Вход…"
    override fun loginConnectFailed(detail: String?) =
        "Не удалось подключиться к серверу: ${detail ?: "неизвестная ошибка"}"

    override val passwordShow = "Показать пароль"
    override val passwordHide = "Скрыть пароль"

    override val projectsTitle = "Проекты"
    override val projectsLoading = "Загрузка проектов…"
    override val projectsEmpty = "У вас пока нет проектов."
    override val projectActive = "активен"
    override val projectInactive = "выключен"

    override val profileTitle = "Профиль"
    override val profileServer = "Сервер"
    override val profileLogout = "Выйти"

    override val settingsTitle = "Настройки"
    override val settingsNotifications = "Уведомления"
    override val settingsNotificationsHint =
        "Пуш и колокольчик: общие правила и отдельно по каждому проекту"
    override val settingsLanguage = "Язык"
    override val settingsLanguageHint = "Язык приложения. Сохраняется в профиле — панель читает тот же."
    override val languageTitle = "Язык приложения"
    override val languageSaving = "Сохраняем в профиль…"
    override val languageSaveFailed = "Язык переключён здесь, но в профиль не записался — попробуйте ещё раз."

    override val agentTitle = "Диалог с агентом"
    override val agentConnecting = "Подключаем агента…"
    override val agentOpenedInBrowser = "Агент открыт в браузере"

    override val tasksNew = "Новая задача"
    override val tasksLoading = "Загрузка задач…"
    override val tasksEmpty = "В проекте пока нет задач."
    override val taskTitleLabel = "Заголовок"
    override val taskTitlePlaceholder = "Что нужно сделать"
    override val taskDescriptionLabel = "Описание"
    override val taskDescriptionPlaceholder = "Подробности (необязательно)"
    override val taskCreate = "Создать"
    override val taskCreating = "Создание…"
    override val taskFallbackTitle = "Задача"
    override val taskLoading = "Загрузка задачи…"

    override fun taskRunCount(count: Int) =
        "$count ${ruPlural(count, "запуск", "запуска", "запусков")}"

    override val taskRunPipeline = "Запустить пайплайн"
    override val taskRunAgain = "Запустить ещё раз"
    override val taskRunning = "Выполняется…"
    override val taskStopRun = "Остановить запуск"
    override val taskStopRequested = "Остановка запрошена…"
    override val taskDiscussion = "Обсуждение"
    override val taskNoComments = "Пока нет комментариев."
    override val taskCommentLabel = "Новый комментарий"
    override val taskCommentPlaceholder = "Написать…"
    override val taskCommentSend = "Отправить"
    override fun taskCommentAttach(count: Int) =
        if (count == 1) "Прикрепить файл" else "Прикрепить файлы"

    override val taskGoToRun = "Перейти к запуску"
    override fun taskRunNumber(number: Int) = "Запуск #$number"

    override val authorAgent = "агент"
    override val authorSystem = "система"
    override val authorHuman = "человек"

    override fun taskState(status: String) = when (status) {
        "new" -> "новая"
        "queued" -> "в очереди"
        "running" -> "выполняется"
        "waiting_input" -> "ждёт ответа"
        "waiting_review" -> "на проверке"
        "done" -> "готово"
        "failed" -> "ошибка"
        "cancelled" -> "отменена"
        "ignored" -> "пропущена"
        else -> null
    }

    override fun attachmentCount(count: Int) = if (count == 1) "1 файл" else "$count файла(ов)"
    override val attachmentPhoto = "Фото"
    override val attachmentFile = "Файл"
    override val attachmentRemove = "Убрать"
    override val attachmentDelete = "Удалить"
    override val attachmentDeleting = "Удаление…"
    override val attachmentUnviewable =
        "Этот файл нельзя показать в приложении, но он прикреплён к задаче."

    override fun attachmentUploading(index: Int, total: Int, fileName: String) =
        "Загрузка $index из $total: $fileName"

    override fun bytes(count: Long) = when {
        count < 1024L -> "$count Б"
        count < 1024L * 1024L -> "${count / 1024L} КБ"
        else -> "${count / (1024L * 1024L)},${(count * 10L / (1024L * 1024L)) % 10L} МБ"
    }

    override val runsTitle = "Запуски"
    override val runsLoading = "Загрузка запусков…"
    override fun runsRunningNow(count: Int) = "$count идёт сейчас"
    override fun runsActiveHeader(count: Int) = "Идут сейчас ($count)"
    override val runsIdle = "Сейчас ничего не выполняется."
    override val runsRecent = "Завершились недавно"
    override val runFallbackTitle = "Запуск"
    override val runLoading = "Загрузка запуска…"
    override val runWaitingAnswer = "ждёт ответа"
    override fun runWaitingAnswerCount(count: Int) = "ждёт ответа ($count)"
    override fun runNumberTitle(number: Int) = "Запуск #$number"

    override fun runState(status: String) = when (status) {
        "pending" -> "в очереди"
        "running" -> "выполняется"
        "waiting_input" -> "ждёт ответа"
        "succeeded" -> "успешно"
        "failed" -> "ошибка"
        "cancelled" -> "отменён"
        else -> null
    }

    override fun stageState(status: String) = when (status) {
        "pending" -> "ждёт"
        "running" -> "идёт"
        "waiting_input" -> "ждёт ответа"
        "succeeded" -> "готово"
        "failed" -> "ошибка"
        "skipped" -> "пропущено"
        else -> null
    }

    override val runFactStatus = "Статус"
    override val runFactDuration = "Длительность"
    override val runFactTokens = "Токены"
    override val runInstructionTitle = "Что просили"
    override val runInstructionFromComment = "из комментария"
    override val runInstructionFromTask = "из описания задачи"
    override val runInstructionCollapse = "Свернуть"
    override val runInstructionExpand = "Показать полностью"
    override val runResultTitle = "Результат запуска"
    override val runQuestionsTitle = "Вопросы агента"
    override val runStagesTitle = "Этапы"
    override val runWorkerSummaryTitle = "Итог воркера"
    override val runLogTitle = "Лог выполнения"
    override val runRawResultTitle = "Полный ответ воркера"
    override val runRawResultLabel = "результат"
    override val runStageOutputLabel = "вывод этапа"
    override val runOpenTask = "Открыть задачу"
    override val runTaskWord = "Задача"

    override fun tokensTotal(text: String) = "$text токенов"
    override fun tokensInput(text: String) = "вход $text"
    override fun tokensOutput(text: String) = "выход $text"
    override fun tokensCache(text: String) = "кэш $text"
    override fun tokensBadge(text: String) = "$text ткн"
    override fun tokensShort(text: String) = "$text ткн"

    override fun logLines(count: Int) =
        "$count ${ruPlural(count, "строка", "строки", "строк")}"

    override val logLevelAll = "все"
    override fun logLevel(level: String) = when (level) {
        "error" -> "ошибки"
        "warn" -> "предупреждения"
        "info" -> "инфо"
        "debug" -> "отладка"
        else -> null
    }

    override val diffTitle = "Изменения"
    override fun diffFrom(sha: String, files: Int) = "от $sha · $files файл(ов), "
    override fun diffApplied(at: String, commit: String) = " · применено $at, коммит $commit"
    override val diffNotPushed = " · в репозиторий не отправлено"
    override val diffTruncated = "Патч обрезан по лимиту размера — показана часть изменений."
    override val diffEmpty = "Дифф пуст."
    override val diffBinary = "Бинарный файл — содержимое не показывается"
    override val diffNoLines = "Нет строк для показа"

    override val inboxTitle = "Входящие"
    override fun inboxNeedAction(count: Int) = "$count требуют действия"
    override val inboxEmpty = "Ничего не ждёт вашего участия."
    override val inboxTabActionable = "Требуют действия"
    override val inboxTabFeed = "Лента"
    override val inboxSwipeNotifications = "Уведомления"
    override val inboxSwipeDismiss = "Не требует действий"
    override val inboxHiddenShown = "Скрытые показаны — вернуть список"
    override fun inboxHiddenCount(count: Int) = "Скрытые: $count — показать"
    override val inboxHiddenMark = "скрыто вами"
    override val inboxAlreadyDecided = "Это уже решено — список сейчас обновится."
    override val actionAlreadyDecided = "Это уже решено — экран сейчас обновится."

    override val actionRequiredTitle = "Требуется ваше участие"
    override fun actionRequiredTitleCount(count: Int) = "Требуется ваше участие ($count)"

    override fun waitingFor(age: String) = "ждёт $age"
    override fun answerAwaitedFor(left: String) = "ответ ждут ещё $left"
    override fun notifyConfigure(label: String) = "$label · настроить"

    override val activitiesLoading = "Загрузка активностей…"
    override val activitiesEmpty = "Пока ничего не происходило."
    override val activitiesLoadingMore = "Загружается…"
    override val activitiesShowMore = "Показать ещё"

    override val interactionWaiting = "Агент ждёт ответа"
    override val interactionNoFields = "Вопрос без полей — подтвердите или откажитесь."
    override fun interactionDeadline(at: String, left: String) =
        "Ответ ждут до $at" + if (left.isEmpty()) "" else " (осталось $left)"

    override fun interactionMissing(fields: String) = "Заполните: $fields"
    override val interactionAnswer = "Ответить"
    override val interactionSkip = "Пропустить"
    override val interactionCancel = "Отменить"
    override val interactionOwnOption = "Другое"
    override val interactionOwnOptionLabel = "Свой вариант"
    override val interactionAnswerPlaceholder = "Ответ…"
    override val interactionNumberPlaceholder = "Число"
    override fun interactionClosed(state: String) = "Этот вопрос уже закрыт — $state"

    override fun interactionState(status: String, result: String?, who: String?) = when (status) {
        "pending" -> "ждёт ответа"
        "answered" -> when (result) {
            "accept" -> if (who != null) "ответил(а) $who" else "получен ответ"
            "decline" -> "пропущен"
            else -> "отменён"
        }
        "expired" -> "истёк срок ответа"
        "cancelled" -> "отменён"
        "orphaned" -> "запуск прерван"
        else -> status
    }

    override val runOptionsTitle = "Чем запускать"
    override fun runOptionsSummary(summary: String) = "Запустится: $summary"
    override val runOptionsHint = "Нажмите, чтобы выбрать"
    override val runOptionsHarness = "Обвязка"
    override val runOptionsModel = "Модель"
    override val runOptionsLevel = "Уровень рассуждений"
    override val runOptionsDefault = "По умолчанию"
    override val runOptionsPipelineHarness = "обвязка по пайплайну"
    override val runOptionsPipelineModel = "модель по умолчанию"
    override val runOptionsCliLevel = "уровень: как у CLI"
    override fun runOptionsLevelSummary(title: String) = "уровень: ${title.lowercase()}"
    override fun runOptionsAppliesToStages(count: Int) =
        "Выбор применяется ко всем этапам запуска ($count)"

    override val runOptionsAsPipeline = "Как в пайплайне"
    override fun runOptionsAsPipelineNamed(value: String) = "Как в пайплайне ($value)"

    override val proposalTitleWaiting = "Изменения ждут ревью"
    override val proposalTitlePushFailed = "Push не прошёл — решите, что дальше"
    override val proposalTitleResetFailed = "Сброс не прошёл — можно повторить"
    override fun proposalRevision(number: Int) = "Ревизия $number"
    override fun proposalFileStats(files: Int, insertions: Int, deletions: Int) =
        "$files файл(ов), +$insertions/−$deletions"

    override fun proposalOperations(count: Int) = "$count операций"
    override fun proposalBranch(branch: String) = "ветка $branch"
    override val proposalCommitMessage = "Сообщение коммита"
    override val proposalBranchLabel = "Ветка"
    override val proposalPush = "Закоммитить и запушить"
    override val proposalApprove = "Одобрить…"
    override val proposalReject = "Отклонить…"
    override val proposalRejectRetry = "Повторить сброс…"
    override val proposalRejectConfirm =
        "Отклонить ревизию и сбросить воркспейс? Наработки этой ревизии будут удалены с воркера."
    override val proposalRejectSubmit = "Да, отклонить"

    override val approvalTitleFallback = "Примите работу"
    override val approvalApproved = "Уже принято."
    override val approvalRejected = "Уже отклонено."
    override val approvalCancelled = "Заявка снята: воркфлоу отменили."
    override val approvalDecided = "Решение уже принято."
    override fun approvalVerdict(passed: Boolean) =
        "вердикт агента: ${if (passed) "ок" else "не ок"}"

    override fun approvalBranch(branch: String) = "ветка $branch"
    override fun approvalCommit(sha: String) = "коммит $sha"
    override val approvalApproveExplain =
        "Принять работу? Воркфлоу пойдёт дальше по ветке «принято». Комментарий" +
            " не обязателен — он останется в истории решения."
    override val approvalCommentLabel = "Комментарий (не обязательно)"
    override val approvalApproveSubmit = "Да, принять"
    override val approvalRejectExplain =
        "Что не так? Этот текст уедет разработчику как задание на доработку, и" +
            " по задаче начнётся новый круг."
    override val approvalRemarksLabel = "Замечания"
    override val approvalRemarksPlaceholder = "Подвал съезжает на узком экране"
    override val approvalRejectSubmit = "Отклонить и вернуть"
    override val approvalApprove = "Принять…"
    override val approvalReject = "Отклонить…"

    override val notificationsTitle = "Уведомления"
    override val notificationsSaving = "Сохраняется…"
    override val notificationsLoading = "Загрузка настроек…"
    override val notificationsEnvPinned =
        "Политика задана переменной окружения на сервере — правки отсюда сохранятся," +
            " но не подействуют, пока переменную не уберут."
    override val notificationsEnvPinnedShort =
        "На сервере политика задана переменной окружения — правки сохранятся, но" +
            " не подействуют, пока её не уберут."
    override val notificationsDeliveryOnly =
        "Лента активностей пишется всегда; здесь выключается только доставка — пуш на телефон" +
            " и колокольчик в панели."
    override val notificationsDeliveryOnlyShort =
        "Настройка выключает только доставку — пуш на телефон. Строка во входящих и запись" +
            " в ленте появятся в любом случае."
    override val notificationsGeneralRules = "Общие правила"
    override val notificationsAllProjects = "Для всех проектов"
    override val notificationsProjects = "Проекты"
    override val notificationsNoProjects = "Проектов пока нет — настраивать нечего."
    override val notificationsMuteProject = "Отключить всё по проекту"
    override val notificationsMuteExcept = "Кроме типов, для которых ниже выбрано своё значение"
    override val notificationsAllSettings = "Все настройки уведомлений →"
    override fun notificationsRowThisType(projectName: String) =
        "Такие уведомления в проекте «$projectName»"

    override val notificationsRowThisTypeHint =
        "Только этот тип события. Остальное по проекту не меняется."
    override val notificationsRowPipeline = "Только для этого пайплайна"
    override val notificationsRowPipelineHint =
        "Перебивает правило проекта — например «проект молчит, а релиз пишет»."
    override fun notificationsCurrent(line: String) = "Сейчас: $line"
    override val projectUnnamed = "без имени"

    override val channelOn = "вкл"
    override val channelSilent = "тихо"
    override val channelOff = "выкл"
    override val channelInherit = "как в общих"
    override val channelSend = "присылать"
    override val channelSendSilently = "без звука"
    override val channelDoNotSend = "не присылать"

    override val scopeMuteAll = "всё отключено"
    override val scopeMuteProject = "отключены"
    override val scopeDefault = "по умолчанию"
    override val scopeInherit = "как в общих"
    override val scopeEnabled = "включены"
    override val scopeDisabled = "отключены"
    override val scopeOn = "включено"
    override val scopeOff = "выключено"

    override fun scopeSummaryMutedExcept(muteLabel: String, rules: Int) = "$muteLabel, кроме $rules"
    override fun scopeSummaryRules(rules: Int) = "своих правил: $rules"

    override val pushChannelQuestions = "Вопросы агентов"
    override val pushChannelQuestionsHint = "Агент остановился и ждёт ответа"
    override val pushChannelActionable = "Требуют действия"
    override val pushChannelActionableHint = "Ревью изменений, удержанные диффы, сбои push"
    override val pushChannelFailures = "Ошибки запусков"
    override val pushChannelFailuresHint = "Запуск завершился с ошибкой"
    override val pushChannelResults = "Результаты"
    override val pushChannelResultsHint = "Успешные запуски и прочие тихие события"

    override val workersTitle = "Воркеры"
    override val workersTabWorkers = "Воркеры"
    override val workersTabSubscriptions = "Подписки"
    override val workersLoading = "Загрузка воркеров…"
    override fun workersNeedLogin(count: Int) =
        "$count ${ruPlural(count, "воркер", "воркера", "воркеров")} без входа"

    override fun workersSubscriptionsExhausted(count: Int) =
        "$count ${ruPlural(count, "подписка исчерпана", "подписки исчерпаны", "подписок исчерпано")}"

    override val workersNoneRegistered = "Ни один воркер не зарегистрирован."
    override val workersNoneAvailable = "Нет воркеров, которые сейчас могут принять работу."
    override val workersNoSubscriptions =
        "Подписок нет: лимиты появятся, когда воркер впервые отчитается об использовании харнесса."
    override val workerNeedsLogin = "нужен вход"
    override fun workerMaxJobs(count: Int) = "до $count задач"
    override fun workerLastSeen(at: String) = "виден $at"
    override val workerNoHarnesses = "Харнессы не привязаны — лимиты не применяются."
    override fun workerSubscription(name: String) = "Подписка: $name"
    override val workerSubscriptionUnbound = "Подписка не привязана — лимит не отслеживается."
    override fun workerRunningJobs(count: Int) = "идёт $count"
    override fun workerQueuedJobs(count: Int) = "в очереди $count"
    override fun workerMaxConcurrent(count: Int) = "не более $count"
    override val workerAccountMismatch = "Отчёт пришёл от другого аккаунта, чем указан в подписке."
    override fun workerReportedAt(at: String) = "отчёт $at"
    override fun subscriptionWorkers(count: Int) = "Воркеры ($count)"
    override val subscriptionNoWorkers = "Ни один воркер не привязан к этой подписке."
    override val subscriptionNoTelemetry = "Телеметрия лимитов ещё не приходила."
    override fun subscriptionDataAt(at: String) = "Данные на $at"
    override fun subscriptionRemainingPercent(percent: Int) = "осталось $percent%"
    override fun subscriptionUsedPercent(percent: Int) = "$percent%"
    override val subscriptionNoData = "нет данных"
    override fun subscriptionResetAt(at: String, left: String) = "Обновится $at$left"
    override fun subscriptionResetLeft(until: String, sessions: String) =
        " (осталось $until$sessions)"

    override fun subscriptionAlsoSessions(sessions: String) = ", ещё $sessions"

    override fun subscriptionIdle(age: String) = "Лимиты без изменений $age"
    override val subscriptionJustNow = "только что"
    override fun workersShowInactive(count: Int) = "Показать неактивные ($count)"
    override fun workersHideInactive(count: Int) = "Скрыть неактивные ($count)"
    override fun workerLimitUntil(at: String, left: String) = "Лимит закрыт до $at$left"
    override val workerLimitClosed = "Лимит закрыт"
    override fun workerAuthEndedAt(at: String) = "Вход закончился $at"
    override val workerAuthEnded = "Вход закончился"
    override val workerAuthFixClaude =
        "Продлить подписку и войти заново можно только в браузере на самой машине воркера" +
            " — «claude auth login» под тем пользователем, от которого он работает."
    override val workerAuthFixOther =
        "Продлить подписку и войти заново можно только в браузере на самой машине воркера."
    override val workerAuthQueueNote =
        "Задачи этого харнесса стоят в очереди и продолжатся сами через пару минут после входа."

    override fun subscriptionState(status: String) = when (status) {
        "available" -> "доступен"
        "exhausted" -> "лимит исчерпан"
        "unauthorized" -> "нужен вход"
        "disabled" -> "выключен"
        else -> null
    }

    override fun workerContactState(state: String) = when (state) {
        "online" -> "на связи"
        "offline" -> "офлайн"
        else -> "ни разу не выходил на связь"
    }

    override fun workerStatusWord(status: String) = when (status) {
        "paused" -> "на паузе"
        "revoked" -> "отозван"
        "pending" -> "не подключён"
        else -> null
    }

    override fun remainingSuffix(left: String) = " (осталось $left)"

    override fun jsonFields(count: Int) =
        "$count ${ruPlural(count, "поле", "поля", "полей")}"

    override fun jsonItems(count: Int) =
        "$count ${ruPlural(count, "элемент", "элемента", "элементов")}"

    override fun jsonShowMore(count: Int) = "…показать ещё $count"

    override fun date(year: Int, month: Int, day: Int) =
        "${two(day)}.${two(month)}.$year"

    override fun duration(hours: Long, minutes: Long) = when {
        hours > 0L -> "$hours ч ${minutes % 60L} мин"
        minutes == 0L -> "<1 мин"
        else -> "$minutes мин"
    }

    override fun remaining(hours: Long, minutes: Long) =
        if (hours > 0L) "$hours ч $minutes мин" else "$minutes мин"

    override fun age(minutes: Long, hours: Long, days: Long) = when {
        days > 0L -> "$days дн"
        hours > 0L -> "$hours ч"
        else -> "$minutes мин"
    }

    override fun windowsLeft(count: Long, windowMinutes: Long): String {
        val unit = if (windowMinutes % 60L == 0L) {
            "${windowMinutes / 60L}-часов"
        } else {
            "${windowMinutes}-минутн"
        }
        val noun = ruPlural(
            count,
            one = "полное ${unit}ое окно",
            few = "полных ${unit}ых окна",
            many = "полных ${unit}ых окон",
        )
        return "$count $noun"
    }

    override val filePickerImages = "Изображения"
}

/** Zero-padded two digits — every table's date needs it, none of them needs its own copy. */
internal fun two(value: Int): String = value.toString().padStart(2, '0')
