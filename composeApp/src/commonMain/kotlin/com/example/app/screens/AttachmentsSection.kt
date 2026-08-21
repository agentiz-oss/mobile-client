package com.example.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.example.app.components.AppButton
import com.example.app.data.AttachmentDto
import com.example.app.platform.PickedFile
import com.example.app.theme.AppTheme

/** Thumbnail edge in the read-only gallery. */
private val THUMB_SIZE = 96.dp

/**
 * The files already on a task, as a strip under its description — a gallery, not a workspace.
 *
 * Attaching happens where a person is *writing* (a new task, a comment), because that is when they
 * have something to attach and something to say about it. What sits under the description is the
 * result of those moments, so this component only shows and opens; removing a file lives one level
 * in, in [AttachmentViewer], where the reader can already see what they are about to delete.
 */
@Composable
internal fun AttachmentGallery(
    attachments: List<AttachmentDto>,
    onOpen: (AttachmentDto) -> Unit,
    loadBytes: suspend (AttachmentDto) -> ByteArray?,
) {
    if (attachments.isEmpty()) return

    Text(
        text = if (attachments.size == 1) "1 файл" else "${attachments.size} файла(ов)",
        style = AppTheme.Label,
        color = AppTheme.Muted,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            AttachmentCard(attachment = attachment, onOpen = { onOpen(attachment) }, loadBytes = loadBytes)
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: AttachmentDto,
    onOpen: () -> Unit,
    loadBytes: suspend (AttachmentDto) -> ByteArray?,
) {
    Column(
        modifier = Modifier
            .width(THUMB_SIZE + 24.dp)
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .clip(RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Background)
            .clickable { onOpen() },
    ) {
        Box(
            modifier = Modifier.size(width = THUMB_SIZE + 24.dp, height = THUMB_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            if (attachment.isImage) {
                AttachmentThumbnail(attachment, loadBytes)
            } else {
                Text(text = fileGlyph(attachment), style = AppTheme.Title, color = AppTheme.Muted)
            }
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = attachment.fileName,
                style = AppTheme.Label,
                color = AppTheme.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = formatBytes(attachment.sizeBytes), style = AppTheme.Label, color = AppTheme.Muted)
        }
    }
}

/**
 * Decodes the image on the fly. A failed decode falls back to the file glyph rather than an error:
 * the server stores whatever the phone produced, and a HEIC from an iPhone is not decodable on
 * every target the app builds for.
 */
@Composable
private fun AttachmentThumbnail(
    attachment: AttachmentDto,
    loadBytes: suspend (AttachmentDto) -> ByteArray?,
) {
    var bitmap by remember(attachment.id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(attachment.id) { mutableStateOf(false) }

    LaunchedEffect(attachment.id) {
        val bytes = loadBytes(attachment)
        if (bytes == null) {
            failed = true
            return@LaunchedEffect
        }
        bitmap = runCatching { bytes.decodeToImageBitmap() }.getOrElse {
            failed = true
            null
        }
    }

    val image = bitmap
    when {
        image != null -> Image(
            bitmap = image,
            contentDescription = attachment.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        failed -> Text(text = fileGlyph(attachment), style = AppTheme.Title, color = AppTheme.Muted)
        else -> Text(text = "…", style = AppTheme.Label, color = AppTheme.Muted)
    }
}

/**
 * Files chosen but not sent yet, with the two ways to choose more.
 *
 * Staged rather than uploaded on pick: a photo attached to a comment belongs *with* the comment,
 * and uploading on pick would leave the file on the task even when the person then changed their
 * mind and cleared what they were writing.
 */
@Composable
internal fun AttachmentStaging(
    staged: List<PickedFile>,
    busy: Boolean,
    uploadLabel: String?,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AppButton(text = "Фото", onClick = onPickPhotos, enabled = !busy, modifier = Modifier.weight(1f))
        AppButton(text = "Файл", onClick = onPickFiles, enabled = !busy, modifier = Modifier.weight(1f))
    }

    if (uploadLabel != null) {
        Spacer(Modifier.height(8.dp))
        Text(text = uploadLabel, style = AppTheme.Label, color = AppTheme.Warning)
    }

    if (staged.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        staged.forEachIndexed { index, file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${file.fileName} · ${formatBytes(file.bytes.size.toLong())}",
                    style = AppTheme.Label,
                    color = AppTheme.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Убрать",
                    style = AppTheme.Label,
                    color = if (busy) AppTheme.Disabled else AppTheme.Danger,
                    modifier = Modifier.clickable(enabled = !busy) { onRemove(index) },
                )
            }
        }
    }
}

/**
 * Full-screen look at one attachment, and the only place a file can be removed from a task: the
 * reader is looking straight at what they are deleting, which the strip under the description
 * cannot promise at thumbnail size.
 */
@Composable
internal fun AttachmentViewer(
    attachment: AttachmentDto,
    bytes: ByteArray?,
    busy: Boolean,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0F172A))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = remember(attachment.id, bytes) {
            bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = attachment.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
                bytes == null -> Text(text = "Загрузка…", style = AppTheme.Body, color = AppTheme.Background)
                // A file the app cannot render still deserves an answer: it is on the server and
                // the agent will get it, which is the only thing the reader actually needs to know.
                else -> Text(
                    text = "Этот файл нельзя показать в приложении, но он прикреплён к задаче.",
                    style = AppTheme.Body,
                    color = AppTheme.Background,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${attachment.fileName} · ${formatBytes(attachment.sizeBytes)}",
                style = AppTheme.Label,
                color = AppTheme.Background,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(text = if (busy) "Удаление…" else "Удалить", onClick = onDelete, enabled = !busy)
                AppButton(text = "Закрыть", onClick = onClose, enabled = !busy)
            }
        }
    }
}

/** A stand-in for a thumbnail when there is nothing to render — by family, not by extension list. */
private fun fileGlyph(attachment: AttachmentDto): String {
    val mime = attachment.mimeType.orEmpty()
    val name = attachment.fileName.lowercase()
    return when {
        mime == "application/pdf" || name.endsWith(".pdf") -> "PDF"
        mime.startsWith("video/") -> "VIDEO"
        mime.startsWith("audio/") -> "AUDIO"
        name.endsWith(".zip") || name.endsWith(".gz") || name.endsWith(".tar") -> "ZIP"
        mime.startsWith("text/") -> "TXT"
        else -> "FILE"
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    // One decimal place, without a formatter: common code has no String.format across targets.
    else -> "${bytes / (1024 * 1024)},${(bytes * 10 / (1024 * 1024)) % 10} МБ"
}
