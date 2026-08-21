package com.example.app.platform

import androidx.compose.runtime.Composable

/**
 * A file the user chose, already read into memory.
 *
 * Bytes rather than a platform handle (a `Uri`, an `NSURL`, a `java.io.File`): the only thing the
 * app does with a pick is POST it, every target can produce bytes, and a handle would drag each
 * platform's lifetime rules — a temporary content Uri revoked when the activity dies, a security
 * scoped iOS URL that has to be released — into common code that has no way to honour them.
 * Attachments are capped server-side at 25 MB, so holding one in memory is bounded.
 */
class PickedFile(
    val fileName: String,
    val mimeType: String?,
    val bytes: ByteArray,
)

/** What a screen calls to open the system picker. Returned by [rememberFilePicker]. */
interface FilePickerLauncher {
    /** The photo picker: the camera roll on a phone, image files elsewhere. */
    fun pickImages()

    /** The document picker: anything, for logs, specs and archives. */
    fun pickFiles()
}

/**
 * Opens the platform's own picker and hands back what the user chose.
 *
 * Two entry points rather than one with a filter, because on a phone these are genuinely different
 * pieces of system UI — the photo picker shows the camera roll and (on both Android 13+ and iOS 14+)
 * needs no permission at all, while the document picker browses files. Offering only the second
 * would make attaching a screenshot a three-tap detour through Files.
 *
 * [onPicked] is called on the main thread with an empty list never — a cancelled pick simply does
 * not call back. Reading the bytes happens off the UI thread on every platform that has one.
 */
@Composable
expect fun rememberFilePicker(onPicked: (List<PickedFile>) -> Unit): FilePickerLauncher
