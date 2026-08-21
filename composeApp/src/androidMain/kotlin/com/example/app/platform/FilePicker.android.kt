package com.example.app.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * `GetMultipleContents` rather than `PickVisualMedia`: one contract serves both entry points by
 * taking the MIME filter at launch time, and it is the same chooser the user already knows from
 * every other app. The photo variant passes `image/*`, which on Android 13+ routes to the system
 * photo picker automatically — no `READ_MEDIA_IMAGES` permission is involved either way, because
 * the chooser hands back a Uri that is already granted to this process.
 */
@Composable
actual fun rememberFilePicker(onPicked: (List<PickedFile>) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The screen recomposes while a picker is open; the callback captured at launch must not be
    // the stale one from the composition that opened it.
    val callback = rememberUpdatedState(onPicked)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            // Reading a content Uri is disk (or worse, a cloud provider) I/O — never on the UI thread.
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { readPickedFile(context, it) } }
            if (files.isNotEmpty()) callback.value(files)
        }
    }

    return remember(launcher, scope) {
        object : FilePickerLauncher {
            override fun pickImages() = launcher.launch("image/*")
            override fun pickFiles() = launcher.launch("*/*")
        }
    }
}

/**
 * Reads one chosen Uri. Returns null rather than throwing: a provider can revoke a Uri between the
 * pick and the read, and one unreadable file must not lose the others the user chose with it.
 */
private fun readPickedFile(context: Context, uri: Uri): PickedFile? = runCatching {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    PickedFile(
        fileName = displayName(context, uri) ?: "file",
        mimeType = resolver.getType(uri),
        bytes = bytes,
    )
}.getOrNull()

/** The name the user sees in the picker; the Uri's own path is an opaque provider id. */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()