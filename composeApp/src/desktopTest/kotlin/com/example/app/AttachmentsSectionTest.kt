package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.app.data.AttachmentDto
import com.example.app.screens.AttachmentsSection
import com.example.app.screens.formatBytes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The attachments block on the task screen: what a person can attach, see and remove before
 * starting a run. The rules that matter are "the count is honest", "a file that is not an image
 * still shows up as something", and "nothing is clickable while an upload is in flight" — a second
 * tap during a slow upload used to be the easy way to send the same photo twice.
 */
class AttachmentsSectionTest {

    private fun photo(id: String = "a1", name: String = "screen.png") =
        AttachmentDto(id = id, fileName = name, mimeType = "image/png", sizeBytes = 2048)

    private fun doc(id: String = "a2", name: String = "spec.pdf") =
        AttachmentDto(id = id, fileName = name, mimeType = "application/pdf", sizeBytes = 5_000_000)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `empty state still offers both ways to attach`() = runComposeUiTest {
        setContent {
            AttachmentsSection(
                attachments = emptyList(),
                busy = false,
                uploadLabel = null,
                onPickPhotos = {}, onPickFiles = {}, onOpen = {}, onDelete = {},
                loadBytes = { null },
            )
        }

        onNodeWithText("Файлы").assertExists()
        onNodeWithText("Фото").assertExists()
        onNodeWithText("Файл").assertExists()
        onNodeWithText("Агент получит эти файлы при запуске.").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `lists what is attached with its size`() = runComposeUiTest {
        setContent {
            AttachmentsSection(
                attachments = listOf(photo(), doc()),
                busy = false,
                uploadLabel = null,
                onPickPhotos = {}, onPickFiles = {}, onOpen = {}, onDelete = {},
                loadBytes = { null },
            )
        }

        onNodeWithText("Файлы (2)").assertExists()
        onNodeWithText("screen.png").assertExists()
        onNodeWithText("spec.pdf").assertExists()
        onNodeWithText("2 КБ").assertExists()
        // A non-image never had a thumbnail to fall back from — it is labelled by family.
        onNodeWithText("PDF").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an upload in flight blocks a second one and says what it is doing`() = runComposeUiTest {
        var photoTaps = 0
        var deletes = 0
        setContent {
            AttachmentsSection(
                attachments = listOf(photo()),
                busy = true,
                uploadLabel = "Загрузка 1 из 2: screen.png",
                onPickPhotos = { photoTaps++ }, onPickFiles = {},
                onOpen = {}, onDelete = { deletes++ },
                loadBytes = { null },
            )
        }

        onNodeWithText("Загрузка 1 из 2: screen.png").assertExists()
        onNodeWithText("Фото").performClick()
        onNodeWithText("Удалить").performClick()
        assertEquals(0, photoTaps, "a picker must not open while an upload is running")
        assertEquals(0, deletes, "delete must not fire while an upload is running")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `opening and deleting reach the caller when idle`() = runComposeUiTest {
        var opened: AttachmentDto? = null
        var deleted: AttachmentDto? = null
        setContent {
            AttachmentsSection(
                attachments = listOf(doc()),
                busy = false,
                uploadLabel = null,
                onPickPhotos = {}, onPickFiles = {},
                onOpen = { opened = it }, onDelete = { deleted = it },
                loadBytes = { null },
            )
        }

        onNodeWithText("PDF").performClick()
        onNodeWithText("Удалить").performClick()
        assertEquals("spec.pdf", opened?.fileName)
        assertEquals("spec.pdf", deleted?.fileName)
    }

    @Test
    fun `sizes read the way a person would say them`() {
        assertEquals("512 Б", formatBytes(512))
        assertEquals("2 КБ", formatBytes(2048))
        assertEquals("1,5 МБ", formatBytes(1_572_864))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an image with no bytes yet does not claim to be broken`() = runComposeUiTest {
        setContent {
            AttachmentsSection(
                attachments = listOf(photo()),
                busy = false,
                uploadLabel = null,
                onPickPhotos = {}, onPickFiles = {}, onOpen = {}, onDelete = {},
                // Never answers — the state a thumbnail is in while its bytes are on the wire.
                loadBytes = { kotlinx.coroutines.awaitCancellation() },
            )
        }

        onNodeWithText("…").assertExists()
        onAllNodesWithText("FILE").assertCountEquals(0)
    }
}
