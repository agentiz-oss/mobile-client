package com.example.app.i18n

/**
 * Spanish, in the neutral register that reads the same in Spain and in Latin America: **usted is
 * avoided along with tú** — the app addresses nobody directly where it can help it («Iniciá sesión»
 * vs «Inicia sesión» is a choice that marks a region, so the imperative is used only where a button
 * has to be a verb, and then in the peninsular form, which is the one every reader parses).
 *
 * Same two conventions as [StringsEn]: Agentiz's own vocabulary stays untranslated (worker, harness,
 * pipeline, run, commit, push, diff — these are the words the server logs and the panel shows), and
 * dates are `05/08/2026`, day first, which is the convention in every Spanish-speaking country.
 */
internal object StringsEs : Strings {

    override val lang = Lang.Es

    override fun networkError(detail: String?) = "Error de red: ${detail ?: "error desconocido"}"
    override val networkErrorShort = "Error de red"

    override fun fileUploadFailed(status: Int) = "No se pudo subir el archivo (HTTP $status)"
    override val fileDeleteFailed = "No se pudo eliminar el archivo"

    override val retry = "Reintentar"
    override val back = "Atrás"
    override val cancel = "Cancelar"
    override val close = "Cerrar"
    override val loading = "Cargando…"
    override val sending = "Enviando…"
    override val yes = "Sí"
    override val no = "No"
    override val other = "Otro"

    override val menu = "Menú"
    override val openMenu = "Abrir el menú"
    override val settings = "Ajustes"
    override val profile = "Perfil"
    override val backAction = "Atrás"

    override fun version(name: String) = "Versión $name"

    override val menuProjects = "Proyectos"
    override fun menuTasksOf(projectName: String) = "Tareas: $projectName"
    override val menuRuns = "Ejecuciones"
    override fun menuRunsCount(count: Int) = "Ejecuciones ($count)"
    override val menuInbox = "Bandeja"
    override fun menuInboxCount(count: Int) = "Bandeja ($count)"
    override val menuWorkers = "Workers"
    override fun menuWorkersCount(count: Int) = "Workers ($count)"
    override val menuAgent = "Agente"

    override val projectFallbackName = "Proyecto"

    override val loginPrompt = "Inicia sesión para ver tus proyectos"
    override val loginServer = "Servidor"
    override val loginName = "Usuario"
    override val loginPassword = "Contraseña"
    override val loginSubmit = "Entrar"
    override val loginBusy = "Entrando…"
    override fun loginConnectFailed(detail: String?) =
        "No se pudo conectar con el servidor: ${detail ?: "error desconocido"}"

    override val passwordShow = "Mostrar la contraseña"
    override val passwordHide = "Ocultar la contraseña"

    override val projectsTitle = "Proyectos"
    override val projectsLoading = "Cargando los proyectos…"
    override val projectsEmpty = "Todavía no tienes proyectos."
    override val projectActive = "activo"
    override val projectInactive = "apagado"

    override val profileTitle = "Perfil"
    override val profileServer = "Servidor"
    override val profileLogout = "Cerrar sesión"

    override val settingsTitle = "Ajustes"
    override val settingsNotifications = "Notificaciones"
    override val settingsNotificationsHint =
        "Push y campana: reglas generales y por proyecto"
    override val settingsLanguage = "Idioma"
    override val settingsLanguageHint = "El idioma de la app. Se guarda en el perfil — el panel lee el mismo."
    override val languageTitle = "Idioma de la app"
    override val languageSaving = "Guardando en el perfil…"
    override val languageSaveFailed = "Cambiado aquí, pero no se guardó en el perfil — inténtalo otra vez."

    override val agentTitle = "Conversación con el agente"
    override val agentConnecting = "Conectando el agente…"
    override val agentOpenedInBrowser = "El agente se abrió en el navegador"

    override val tasksNew = "Nueva tarea"
    override val tasksLoading = "Cargando las tareas…"
    override val tasksEmpty = "El proyecto todavía no tiene tareas."
    override val taskTitleLabel = "Título"
    override val taskTitlePlaceholder = "Qué hay que hacer"
    override val taskDescriptionLabel = "Descripción"
    override val taskDescriptionPlaceholder = "Detalles (opcional)"
    override val taskCreate = "Crear"
    override val taskCreating = "Creando…"
    override val taskFallbackTitle = "Tarea"
    override val taskLoading = "Cargando la tarea…"

    override fun taskRunCount(count: Int) =
        "$count ${enPlural(count, "ejecución", "ejecuciones")}"

    override val taskRunPipeline = "Ejecutar el pipeline"
    override val taskRunAgain = "Ejecutar otra vez"
    override val taskRunning = "En ejecución…"
    override val taskStopRun = "Detener la ejecución"
    override val taskStopRequested = "Parada solicitada…"
    override val taskDiscussion = "Conversación"
    override val taskNoComments = "Todavía no hay comentarios."
    override val taskCommentLabel = "Nuevo comentario"
    override val taskCommentPlaceholder = "Escribir…"
    override val taskCommentSend = "Enviar"
    override fun taskCommentAttach(count: Int) =
        if (count == 1) "Adjuntar el archivo" else "Adjuntar los archivos"

    override val taskGoToRun = "Ir a una ejecución"
    override fun taskRunNumber(number: Int) = "Ejecución n.º $number"

    override val authorAgent = "agente"
    override val authorSystem = "sistema"
    override val authorHuman = "persona"

    override fun taskState(status: String) = when (status) {
        "new" -> "nueva"
        "queued" -> "en cola"
        "running" -> "en ejecución"
        "waiting_input" -> "espera respuesta"
        "waiting_review" -> "en revisión"
        "done" -> "lista"
        "failed" -> "error"
        "cancelled" -> "cancelada"
        "ignored" -> "omitida"
        else -> null
    }

    override fun attachmentCount(count: Int) =
        "$count ${enPlural(count, "archivo", "archivos")}"

    override val attachmentPhoto = "Foto"
    override val attachmentFile = "Archivo"
    override val attachmentRemove = "Quitar"
    override val attachmentDelete = "Eliminar"
    override val attachmentDeleting = "Eliminando…"
    override val attachmentUnviewable =
        "Este archivo no se puede mostrar en la app, pero está adjunto a la tarea."

    override fun attachmentUploading(index: Int, total: Int, fileName: String) =
        "Subiendo $index de $total: $fileName"

    override fun bytes(count: Long) = when {
        count < 1024L -> "$count B"
        count < 1024L * 1024L -> "${count / 1024L} kB"
        else -> "${count / (1024L * 1024L)},${(count * 10L / (1024L * 1024L)) % 10L} MB"
    }

    override val runsTitle = "Ejecuciones"
    override val runsLoading = "Cargando las ejecuciones…"
    override fun runsRunningNow(count: Int) = "$count en curso"
    override fun runsActiveHeader(count: Int) = "En curso ($count)"
    override val runsIdle = "Ahora mismo no se está ejecutando nada."
    override val runsRecent = "Terminadas hace poco"
    override val runFallbackTitle = "Ejecución"
    override val runLoading = "Cargando la ejecución…"
    override val runWaitingAnswer = "espera respuesta"
    override fun runWaitingAnswerCount(count: Int) = "espera respuesta ($count)"
    override fun runNumberTitle(number: Int) = "Ejecución n.º $number"

    override fun runState(status: String) = when (status) {
        "pending" -> "en cola"
        "running" -> "en ejecución"
        "waiting_input" -> "espera respuesta"
        "succeeded" -> "correcta"
        "failed" -> "error"
        "cancelled" -> "cancelada"
        else -> null
    }

    override fun stageState(status: String) = when (status) {
        "pending" -> "en espera"
        "running" -> "en curso"
        "waiting_input" -> "espera respuesta"
        "succeeded" -> "lista"
        "failed" -> "error"
        "skipped" -> "omitida"
        else -> null
    }

    override val runFactStatus = "Estado"
    override val runFactDuration = "Duración"
    override val runFactTokens = "Tokens"
    override val runInstructionTitle = "Qué se pidió"
    override val runInstructionFromComment = "de un comentario"
    override val runInstructionFromTask = "de la descripción de la tarea"
    override val runInstructionCollapse = "Contraer"
    override val runInstructionExpand = "Ver completo"
    override val runResultTitle = "Resultado de la ejecución"
    override val runQuestionsTitle = "Preguntas del agente"
    override val runStagesTitle = "Etapas"
    override val runWorkerSummaryTitle = "Resumen del worker"
    override val runLogTitle = "Registro de ejecución"
    override val runRawResultTitle = "Respuesta completa del worker"
    override val runRawResultLabel = "resultado"
    override val runStageOutputLabel = "salida de la etapa"
    override val runOpenTask = "Abrir la tarea"
    override val runTaskWord = "Tarea"

    override fun tokensTotal(text: String) = "$text tokens"
    override fun tokensInput(text: String) = "entrada $text"
    override fun tokensOutput(text: String) = "salida $text"
    override fun tokensCache(text: String) = "caché $text"
    override fun tokensBadge(text: String) = "$text tk"
    override fun tokensShort(text: String) = "$text tk"

    override fun logLines(count: Int) = "$count ${enPlural(count, "línea", "líneas")}"

    override val logLevelAll = "todo"
    override fun logLevel(level: String) = when (level) {
        "error" -> "errores"
        "warn" -> "avisos"
        "info" -> "info"
        "debug" -> "depuración"
        else -> null
    }

    override val diffTitle = "Cambios"
    override fun diffFrom(sha: String, files: Int) =
        "desde $sha · $files ${enPlural(files, "archivo", "archivos")}, "

    override fun diffApplied(at: String, commit: String) = " · aplicado $at, commit $commit"
    override val diffNotPushed = " · no se envió al repositorio"
    override val diffTruncated =
        "El parche se cortó por el límite de tamaño — esto es parte de los cambios."
    override val diffEmpty = "El diff está vacío."
    override val diffBinary = "Archivo binario — no se muestra el contenido"
    override val diffNoLines = "No hay líneas que mostrar"

    override val inboxTitle = "Bandeja"
    override fun inboxNeedAction(count: Int) =
        "$count ${enPlural(count, "requiere", "requieren")} tu atención"

    override val inboxEmpty = "Nada espera tu participación."
    override val inboxTabActionable = "Requieren acción"
    override val inboxTabFeed = "Actividad"
    override val inboxSwipeNotifications = "Notificaciones"
    override val inboxSwipeDismiss = "No requiere acción"
    override val inboxHiddenShown = "Se muestran las ocultas — volver a la lista"
    override fun inboxHiddenCount(count: Int) = "Ocultas: $count — mostrar"
    override val inboxHiddenMark = "ocultada por ti"
    override val inboxAlreadyDecided = "Ya está resuelto — la lista se actualizará enseguida."
    override val actionAlreadyDecided = "Ya está resuelto — la pantalla se actualizará enseguida."

    override val actionRequiredTitle = "Requiere tu participación"
    override fun actionRequiredTitleCount(count: Int) = "Requiere tu participación ($count)"

    override fun waitingFor(age: String) = "espera $age"
    override fun answerAwaitedFor(left: String) = "queda $left para responder"
    override fun notifyConfigure(label: String) = "$label · configurar"

    override val activitiesLoading = "Cargando la actividad…"
    override val activitiesEmpty = "Todavía no ha pasado nada."
    override val activitiesLoadingMore = "Cargando…"
    override val activitiesShowMore = "Ver más"

    override val interactionWaiting = "El agente espera una respuesta"
    override val interactionNoFields = "Una pregunta sin campos — confirma o rechaza."
    override fun interactionDeadline(at: String, left: String) =
        "Se espera respuesta hasta $at" + if (left.isEmpty()) "" else " (quedan $left)"

    override fun interactionMissing(fields: String) = "Completa: $fields"
    override val interactionAnswer = "Responder"
    override val interactionSkip = "Omitir"
    override val interactionCancel = "Cancelar"
    override val interactionOwnOption = "Otro"
    override val interactionOwnOptionLabel = "Tu propia respuesta"
    override val interactionAnswerPlaceholder = "Respuesta…"
    override val interactionNumberPlaceholder = "Número"
    override fun interactionClosed(state: String) = "Esta pregunta ya está cerrada — $state"

    override fun interactionState(status: String, result: String?, who: String?) = when (status) {
        "pending" -> "espera respuesta"
        "answered" -> when (result) {
            "accept" -> if (who != null) "respondió $who" else "respondida"
            "decline" -> "omitida"
            else -> "cancelada"
        }
        "expired" -> "venció el plazo de respuesta"
        "cancelled" -> "cancelada"
        "orphaned" -> "la ejecución se interrumpió"
        else -> status
    }

    override val runOptionsTitle = "Con qué ejecutar"
    override fun runOptionsSummary(summary: String) = "Se ejecutará: $summary"
    override val runOptionsHint = "Toca para elegir"
    override val runOptionsHarness = "Harness"
    override val runOptionsModel = "Modelo"
    override val runOptionsLevel = "Nivel de razonamiento"
    override val runOptionsDefault = "Por defecto"
    override val runOptionsPipelineHarness = "el harness del pipeline"
    override val runOptionsPipelineModel = "el modelo por defecto"
    override val runOptionsCliLevel = "nivel: el de la CLI"
    override fun runOptionsLevelSummary(title: String) = "nivel: ${title.lowercase()}"
    override fun runOptionsAppliesToStages(count: Int) =
        "La elección se aplica a todas las etapas de la ejecución ($count)"

    override val runOptionsAsPipeline = "Como en el pipeline"
    override fun runOptionsAsPipelineNamed(value: String) = "Como en el pipeline ($value)"

    override val proposalTitleWaiting = "Los cambios esperan revisión"
    override val proposalTitlePushFailed = "El push falló — decide qué sigue"
    override val proposalTitleResetFailed = "El reset falló — se puede reintentar"
    override fun proposalRevision(number: Int) = "Revisión $number"
    override fun proposalFileStats(files: Int, insertions: Int, deletions: Int) =
        "$files ${enPlural(files, "archivo", "archivos")}, +$insertions/−$deletions"

    override fun proposalOperations(count: Int) =
        "$count ${enPlural(count, "operación", "operaciones")}"

    override fun proposalBranch(branch: String) = "rama $branch"
    override val proposalCommitMessage = "Mensaje del commit"
    override val proposalBranchLabel = "Rama"
    override val proposalPush = "Hacer commit y push"
    override val proposalApprove = "Aprobar…"
    override val proposalReject = "Rechazar…"
    override val proposalRejectRetry = "Reintentar el reset…"
    override val proposalRejectConfirm =
        "¿Rechazar esta revisión y restablecer el workspace? Todo lo que hizo esta revisión se" +
            " eliminará del worker."
    override val proposalRejectSubmit = "Sí, rechazar"

    override val approvalTitleFallback = "Acepta el trabajo"
    override val approvalApproved = "Ya está aceptado."
    override val approvalRejected = "Ya está rechazado."
    override val approvalCancelled = "Retirada: se canceló el workflow."
    override val approvalDecided = "La decisión ya está tomada."
    override fun approvalVerdict(passed: Boolean) =
        "veredicto del agente: ${if (passed) "ok" else "no ok"}"

    override fun approvalBranch(branch: String) = "rama $branch"
    override fun approvalCommit(sha: String) = "commit $sha"
    override val approvalApproveExplain =
        "¿Aceptar el trabajo? El workflow seguirá por su rama «aceptado». El comentario es" +
            " opcional — queda en el historial de la decisión."
    override val approvalCommentLabel = "Comentario (opcional)"
    override val approvalApproveSubmit = "Sí, aceptar"
    override val approvalRejectExplain =
        "¿Qué está mal? Este texto le llega al desarrollador como el encargo de la siguiente" +
            " vuelta de trabajo sobre la tarea."
    override val approvalRemarksLabel = "Observaciones"
    override val approvalRemarksPlaceholder = "El pie se descoloca en pantallas estrechas"
    override val approvalRejectSubmit = "Rechazar y devolver"
    override val approvalApprove = "Aceptar…"
    override val approvalReject = "Rechazar…"

    override val notificationsTitle = "Notificaciones"
    override val notificationsSaving = "Guardando…"
    override val notificationsLoading = "Cargando los ajustes…"
    override val notificationsEnvPinned =
        "La política está fijada por una variable de entorno en el servidor — los cambios de" +
            " aquí se guardan, pero no surten efecto hasta que se quite esa variable."
    override val notificationsEnvPinnedShort =
        "En el servidor la política está fijada por una variable de entorno — los cambios se" +
            " guardan, pero no surten efecto hasta que se quite."
    override val notificationsDeliveryOnly =
        "La actividad se registra siempre; aquí solo se desactiva la entrega — el push al" +
            " teléfono y la campana del panel."
    override val notificationsDeliveryOnlyShort =
        "Esto desactiva solo la entrega — el push al teléfono. La fila en la bandeja y el" +
            " registro en la actividad aparecen igualmente."
    override val notificationsGeneralRules = "Reglas generales"
    override val notificationsAllProjects = "Para todos los proyectos"
    override val notificationsProjects = "Proyectos"
    override val notificationsNoProjects = "Todavía no hay proyectos — no hay nada que configurar."
    override val notificationsMuteProject = "Silenciar todo el proyecto"
    override val notificationsMuteExcept = "Salvo los tipos con un valor propio elegido abajo"
    override val notificationsAllSettings = "Todos los ajustes de notificaciones →"
    override fun notificationsRowThisType(projectName: String) =
        "Notificaciones como esta en «$projectName»"

    override val notificationsRowThisTypeHint =
        "Solo este tipo de evento. El resto del proyecto no cambia."
    override val notificationsRowPipeline = "Solo para este pipeline"
    override val notificationsRowPipelineHint =
        "Tiene prioridad sobre la regla del proyecto — «el proyecto calla, pero la release avisa»."
    override fun notificationsCurrent(line: String) = "Ahora: $line"
    override val projectUnnamed = "sin nombre"

    override val channelOn = "sí"
    override val channelSilent = "en silencio"
    override val channelOff = "no"
    override val channelInherit = "como en general"
    override val channelSend = "enviar"
    override val channelSendSilently = "sin sonido"
    override val channelDoNotSend = "no enviar"

    override val scopeMuteAll = "todo silenciado"
    override val scopeMuteProject = "silenciadas"
    override val scopeDefault = "por defecto"
    override val scopeInherit = "como en general"
    override val scopeEnabled = "activadas"
    override val scopeDisabled = "silenciadas"
    override val scopeOn = "activado"
    override val scopeOff = "desactivado"

    override fun scopeSummaryMutedExcept(muteLabel: String, rules: Int) =
        "$muteLabel, salvo $rules"

    override fun scopeSummaryRules(rules: Int) = "reglas propias: $rules"

    override val pushChannelQuestions = "Preguntas de los agentes"
    override val pushChannelQuestionsHint = "Un agente se detuvo y espera una respuesta"
    override val pushChannelActionable = "Requieren acción"
    override val pushChannelActionableHint = "Revisión de cambios, diffs retenidos, push fallidos"
    override val pushChannelFailures = "Errores de ejecución"
    override val pushChannelFailuresHint = "Una ejecución terminó con error"
    override val pushChannelResults = "Resultados"
    override val pushChannelResultsHint = "Ejecuciones correctas y otros eventos silenciosos"

    override val workersTitle = "Workers"
    override val workersTabWorkers = "Workers"
    override val workersTabSubscriptions = "Suscripciones"
    override val workersLoading = "Cargando los workers…"
    override fun workersNeedLogin(count: Int) =
        "$count ${enPlural(count, "worker", "workers")} sin sesión"

    override fun workersSubscriptionsExhausted(count: Int) =
        "$count ${enPlural(count, "suscripción agotada", "suscripciones agotadas")}"

    override val workersNoneRegistered = "No hay ningún worker registrado."
    override val workersNoneAvailable = "Ningún worker puede aceptar trabajo ahora mismo."
    override val workersNoSubscriptions =
        "No hay suscripciones: los límites aparecen cuando un worker informa por primera vez del" +
            " uso de su harness."
    override val workerNeedsLogin = "falta iniciar sesión"
    override fun workerMaxJobs(count: Int) = "hasta $count ${enPlural(count, "tarea", "tareas")}"
    override fun workerLastSeen(at: String) = "visto $at"
    override val workerNoHarnesses = "No hay harness vinculado — no se aplican límites."
    override fun workerSubscription(name: String) = "Suscripción: $name"
    override val workerSubscriptionUnbound =
        "No hay suscripción vinculada — el límite no se controla."
    override fun workerRunningJobs(count: Int) = "$count en curso"
    override fun workerQueuedJobs(count: Int) = "$count en cola"
    override fun workerMaxConcurrent(count: Int) = "no más de $count"
    override val workerAccountMismatch =
        "El informe llegó de una cuenta distinta de la que indica la suscripción."
    override fun workerReportedAt(at: String) = "informe $at"
    override fun subscriptionWorkers(count: Int) = "Workers ($count)"
    override val subscriptionNoWorkers = "Ningún worker está vinculado a esta suscripción."
    override val subscriptionNoTelemetry = "Todavía no ha llegado telemetría de límites."
    override fun subscriptionDataAt(at: String) = "Datos a $at"
    override fun subscriptionRemainingPercent(percent: Int) = "queda $percent%"
    override fun subscriptionUsedPercent(percent: Int) = "$percent%"
    override val subscriptionNoData = "sin datos"
    override fun subscriptionResetAt(at: String, left: String) = "Se renueva $at$left"
    override fun subscriptionResetLeft(until: String, sessions: String) =
        " (quedan $until$sessions)"

    override fun subscriptionAlsoSessions(sessions: String) = ", $sessions más"
    override fun subscriptionIdle(age: String) = "Límites sin cambios desde hace $age"
    override val subscriptionJustNow = "un momento"
    override fun workersShowInactive(count: Int) = "Mostrar los inactivos ($count)"
    override fun workersHideInactive(count: Int) = "Ocultar los inactivos ($count)"
    override fun workerLimitUntil(at: String, left: String) = "Límite cerrado hasta $at$left"
    override val workerLimitClosed = "Límite cerrado"
    override fun workerAuthEndedAt(at: String) = "La sesión terminó $at"
    override val workerAuthEnded = "La sesión terminó"
    override val workerAuthFixClaude =
        "Renovar la suscripción y volver a entrar solo se puede desde un navegador en la propia" +
            " máquina del worker — «claude auth login», con el usuario con el que se ejecuta."
    override val workerAuthFixOther =
        "Renovar la suscripción y volver a entrar solo se puede desde un navegador en la propia" +
            " máquina del worker."
    override val workerAuthQueueNote =
        "Las tareas de este harness están en cola y continúan solas un par de minutos después de" +
            " iniciar sesión."

    override fun subscriptionState(status: String) = when (status) {
        "available" -> "disponible"
        "exhausted" -> "límite agotado"
        "unauthorized" -> "falta iniciar sesión"
        "disabled" -> "desactivado"
        else -> null
    }

    override fun workerContactState(state: String) = when (state) {
        "online" -> "en línea"
        "offline" -> "sin conexión"
        else -> "nunca se ha puesto en contacto"
    }

    override fun workerStatusWord(status: String) = when (status) {
        "paused" -> "en pausa"
        "revoked" -> "revocado"
        "pending" -> "sin conectar"
        else -> null
    }

    override fun remainingSuffix(left: String) = " (quedan $left)"

    override fun jsonFields(count: Int) = "$count ${enPlural(count, "campo", "campos")}"
    override fun jsonItems(count: Int) = "$count ${enPlural(count, "elemento", "elementos")}"
    override fun jsonShowMore(count: Int) = "…ver $count más"

    override fun date(year: Int, month: Int, day: Int) = "${two(day)}/${two(month)}/$year"

    override fun duration(hours: Long, minutes: Long) = when {
        hours > 0L -> "$hours h ${minutes % 60L} min"
        minutes == 0L -> "<1 min"
        else -> "$minutes min"
    }

    override fun remaining(hours: Long, minutes: Long) =
        if (hours > 0L) "$hours h $minutes min" else "$minutes min"

    override fun age(minutes: Long, hours: Long, days: Long) = when {
        days > 0L -> "$days d"
        hours > 0L -> "$hours h"
        else -> "$minutes min"
    }

    override fun windowsLeft(count: Long, windowMinutes: Long): String {
        val unit = if (windowMinutes % 60L == 0L) {
            "de ${windowMinutes / 60L} h"
        } else {
            "de $windowMinutes min"
        }
        val noun = enPlural(count, "ventana completa", "ventanas completas")
        return "$count $noun $unit"
    }

    override val filePickerImages = "Imágenes"
}
