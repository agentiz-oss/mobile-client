package com.example.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.app.data.AttachmentDto
import com.example.app.platform.PickedFile
import com.example.app.screens.AttachmentGallery
import com.example.app.screens.AttachmentStaging
import com.example.app.screens.AttachmentViewer
import com.example.app.screens.formatBytes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two halves attachments were split into, and why they are apart: the strip under a task's
 * description only shows what is there, and choosing files happens where a person is writing — a
 * new task or a comment. Deleting lives in the viewer, the one place the reader can see the file
 * they are about to lose.
 */
class AttachmentsSectionTest {

    private fun photo(id: String = "a1", name: String = "screen.png") =
        AttachmentDto(id = id, fileName = name, mimeType = "image/png", sizeBytes = 2048)

    private fun doc(id: String = "a2", name: String = "spec.pdf") =
        AttachmentDto(id = id, fileName = name, mimeType = "application/pdf", sizeBytes = 5_000_000)

    private fun picked(name: String) = PickedFile(name, "image/png", ByteArray(1024))

    // --- gallery -------------------------------------------------------------------------

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `gallery names every file and counts them`() = runComposeUiTest {
        setContent { AttachmentGallery(listOf(photo(), doc()), onOpen = {}, loadBytes = { null }) }

        onNodeWithText("2 файла(ов)").assertExists()
        onNodeWithText("screen.png").assertExists()
        onNodeWithText("spec.pdf").assertExists()
        // A non-image has no thumbnail to fall back from; it is labelled by family.
        onNodeWithText("PDF").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `gallery offers no way to attach or delete`() = runComposeUiTest {
        setContent { AttachmentGallery(listOf(photo()), onOpen = {}, loadBytes = { null }) }

        // Both moved out on purpose: attaching to the composers, deleting into the viewer.
        onAllNodesWithText("Фото").assertCountEquals(0)
        onAllNodesWithText("Файл").assertCountEquals(0)
        onAllNodesWithText("Удалить").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an empty gallery draws nothing at all`() = runComposeUiTest {
        setContent { AttachmentGallery(emptyList(), onOpen = {}, loadBytes = { null }) }

        // Not "файлов нет": the strip sits inside the description card, and a permanent empty
        // notice there would be a line of furniture on every task that never has files.
        onAllNodesWithText("0 файла(ов)").assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `tapping a file opens it`() = runComposeUiTest {
        var opened: AttachmentDto? = null
        setContent { AttachmentGallery(listOf(doc()), onOpen = { opened = it }, loadBytes = { null }) }

        onNodeWithText("PDF").performClick()
        assertEquals("spec.pdf", opened?.fileName)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an image with no bytes yet does not claim to be broken`() = runComposeUiTest {
        setContent {
            // Never answers — the state a thumbnail is in while its bytes are on the wire.
            AttachmentGallery(listOf(photo()), onOpen = {}, loadBytes = { kotlinx.coroutines.awaitCancellation() })
        }

        onNodeWithText("…").assertExists()
        onAllNodesWithText("FILE").assertCountEquals(0)
    }

    // --- staging -------------------------------------------------------------------------

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `staging offers both pickers and lists what is waiting to be sent`() = runComposeUiTest {
        setContent {
            AttachmentStaging(
                staged = listOf(picked("shot.png")),
                busy = false, uploadLabel = null,
                onPickPhotos = {}, onPickFiles = {}, onRemove = {},
            )
        }

        onNodeWithText("Фото").assertExists()
        onNodeWithText("Файл").assertExists()
        onNodeWithText("shot.png · 1 КБ").assertExists()
        onNodeWithText("Убрать").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a staged file can be taken back out before it is sent`() = runComposeUiTest {
        var removed = -1
        setContent {
            AttachmentStaging(
                staged = listOf(picked("a.png"), picked("b.png")),
                busy = false, uploadLabel = null,
                onPickPhotos = {}, onPickFiles = {}, onRemove = { removed = it },
            )
        }

        onAllNodesWithText("Убрать")[1].performClick()
        assertEquals(1, removed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an upload in flight blocks another pick and says what it is doing`() = runComposeUiTest {
        var picks = 0
        var removals = 0
        setContent {
            AttachmentStaging(
                staged = listOf(picked("shot.png")),
                busy = true,
                uploadLabel = "Загрузка 1 из 2: shot.png",
                onPickPhotos = { picks++ }, onPickFiles = { picks++ }, onRemove = { removals++ },
            )
        }

        onNodeWithText("Загрузка 1 из 2: shot.png").assertExists()
        onNodeWithText("Фото").performClick()
        onNodeWithText("Убрать").performClick()
        assertEquals(0, picks, "a picker must not open while an upload is running")
        assertEquals(0, removals, "the list must not change under an upload that is reading it")
    }

    // --- viewer --------------------------------------------------------------------------

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the viewer is where a file is deleted`() = runComposeUiTest {
        var deleted = false
        var closed = false
        setContent {
            AttachmentViewer(
                attachment = doc(), bytes = null, busy = false,
                onDelete = { deleted = true }, onClose = { closed = true },
            )
        }

        onNodeWithText("spec.pdf · 4,7 МБ").assertExists()
        onNodeWithText("Удалить").performClick()
        assertEquals(true, deleted)
        onNodeWithText("Закрыть").performClick()
        assertEquals(true, closed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a file the app cannot render is still reported as attached`() = runComposeUiTest {
        setContent {
            AttachmentViewer(
                attachment = doc(), bytes = ByteArray(4) { 0 }, busy = false,
                onDelete = {}, onClose = {},
            )
        }

        onNodeWithText("Этот файл нельзя показать в приложении, но он прикреплён к задаче.").assertExists()
    }

    @Test
    fun `sizes read the way a person would say them`() {
        assertEquals("512 Б", formatBytes(512))
        assertEquals("2 КБ", formatBytes(2048))
        assertEquals("1,5 МБ", formatBytes(1_572_864))
    }
}
