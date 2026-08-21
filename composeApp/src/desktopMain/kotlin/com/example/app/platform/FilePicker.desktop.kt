package com.example.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Extensions the "photo" entry point offers, mirroring what the image thumbnails can render. */
private val IMAGE_EXTENSIONS = arrayOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

/**
 * Swing's chooser. The desktop build exists to run the app on a workstation, so a native-looking
 * dialog is not worth a platform dependency — but the dialog is modal and blocks whichever thread
 * shows it, hence the jump off the UI thread for both the chooser and the reads.
 */
@Composable
actual fun rememberFilePicker(onPicked: (List<PickedFile>) -> Unit): FilePickerLauncher {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)

    return remember(scope) {
        object : FilePickerLauncher {
            private fun choose(imagesOnly: Boolean) {
                scope.launch {
                    val files = withContext(Dispatchers.IO) {
                        val chooser = JFileChooser().apply {
                            isMultiSelectionEnabled = true
                            if (imagesOnly) {
                                fileFilter = FileNameExtensionFilter("Изображения", *IMAGE_EXTENSIONS)
                            }
                        }
                        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                            emptyList()
                        } else {
                            chooser.selectedFiles.orEmpty().mapNotNull { readPickedFile(it) }
                        }
                    }
                    if (files.isNotEmpty()) callback.value(files)
                }
            }

            override fun pickImages() = choose(imagesOnly = true)
            override fun pickFiles() = choose(imagesOnly = false)
        }
    }
}

/** One unreadable file (permissions, a vanished mount) must not lose the rest of the selection. */
private fun readPickedFile(file: File): PickedFile? = runCatching {
    PickedFile(
        fileName = file.name,
        // probeContentType consults the OS table and returns null for anything it does not know,
        // which the server reads as "unspecified" — the same as an octet-stream upload.
        mimeType = runCatching { Files.probeContentType(file.toPath()) }.getOrNull(),
        bytes = file.readBytes(),
    )
}.getOrNull()
