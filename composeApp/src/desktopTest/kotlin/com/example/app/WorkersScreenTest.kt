package com.example.app

import com.example.app.data.WorkerDto
import com.example.app.screens.isInactiveWorker
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkersScreenTest : RussianUiTest() {

    @Test
    fun defaultFleetHidesPausedOfflineAndNeverConnectedWorkers() {
        assertFalse(isInactiveWorker(WorkerDto(id = "online", name = "online", status = "active", contactState = "online")))
        assertTrue(isInactiveWorker(WorkerDto(id = "offline", name = "offline", status = "active", contactState = "offline")))
        assertTrue(isInactiveWorker(WorkerDto(id = "paused", name = "paused", status = "paused", contactState = "online")))
        assertTrue(isInactiveWorker(WorkerDto(id = "new", name = "new", status = "active", contactState = "never_contacted")))
    }
}
