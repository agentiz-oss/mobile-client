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
import com.example.app.theme.AppTheme

/** Thumbnail edge, and the cap the full-screen viewer scales an image down to fit. */
private val THUMB_SIZE = 96.dp

/**
 * The task's files: what is attached, and the two ways to attach more.
 *
 * Images load their own bytes and render as thumbnails, because a screenshot is the attachment a
 * person actually needs to recognise at a glance — a row of file names would make "которая из
 * этих трёх" unanswerable without opening each. Everything else is a labelled card.
 *
 * [loadBytes] is the caller's cache, not a fetch: the task screen re-polls every two seconds while
 * a run is active, and a thumbnail that re-downloaded on every tick would make an open task a
 * steady stream of image traffic.
 */
@Composable
internal fun AttachmentsSection(
    attachments: List<AttachmentDto>,
    busy: Boolean,
    uploadLabel: String?,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
    onOpen: (AttachmentDto) -> Unit,
    onDelete: (AttachmentDto) -> Unit,
    loadBytes: suspend (AttachmentDto) -> ByteArray?,
) {
    SectionTitle(if (attachments.isEmpty()) "Файлы" else "Файлы (${attachments.size})")
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Агент получит эти файлы при запуске.",
        style = AppTheme.Label,
        color = AppTheme.Muted,
    )

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AppButton(text = "Фото", onClick = onPickPhotos, enabled = !busy, modifier = Modifier.weight(1f))
        AppButton(text = "Файл", onClick = onPickFiles, enabled = !busy, modifier = Modifier.weight(1f))
    }

    if (uploadLabel != null) {
        Spacer(Modifier.height(8.dp))
        Text(text = uploadLabel, style = AppTheme.Label, color = AppTheme.Warning)
    }

    if (attachments.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            attachments.forEach { attachment ->
                AttachmentCard(
                    attachment = attachment,
                    busy = busy,
                    onOpen = { onOpen(attachment) },
                    onDelete = { onDelete(attachment) },
                    loadBytes = loadBytes,
                )
            }
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: AttachmentDto,
    busy: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    loadBytes: suspend (AttachmentDto) -> ByteArray?,
) {
    Column(
        modifier = Modifier
            .width(THUMB_SIZE + 24.dp)
            .border(1.dp, AppTheme.Border, RoundedCornerShape(AppTheme.Radius))
            .clip(RoundedCornerShape(AppTheme.Radius))
            .background(AppTheme.Surface),
    ) {
        Box(
            modifier = Modifier
                .size(width = THUMB_SIZE + 24.dp, height = THUMB_SIZE)
                .clickable(enabled = !busy) { onOpen() },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = formatBytes(attachment.sizeBytes), style = AppTheme.Label, color = AppTheme.Muted)
                Text(
                    text = "Удалить",
                    style = AppTheme.Label,
                    color = if (busy) AppTheme.Disabled else AppTheme.Danger,
                    modifier = Modifier.clickable(enabled = !busy) { onDelete() },
                )
            }
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
 * Full-screen look at one image. Tapping the backdrop closes it — the same gesture the platform
 * photo viewers use, and the only one available without a gesture library.
 */
@Composable
internal fun AttachmentViewer(
    attachment: AttachmentDto,
    bytes: ByteArray?,
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
            Spacer(Modifier.height(4.dp))
            Text(text = "Нажмите, чтобы закрыть", style = AppTheme.Label, color = AppTheme.Disabled)
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
