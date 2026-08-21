package com.example.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * A hidden `<input type="file">`, created per pick and discarded with it.
 *
 * The browser build is the "try it out" target, so this stays deliberately plain: no drag-and-drop
 * surface, no clipboard paste — just the same chooser every web page uses. Reading is callback
 * based because `FileReader` is, and the callbacks already run on the browser's single thread.
 */
@Composable
actual fun rememberFilePicker(onPicked: (List<PickedFile>) -> Unit): FilePickerLauncher {
    val callback = rememberUpdatedState(onPicked)

    return remember {
        object : FilePickerLauncher {
            private fun choose(accept: String) {
                val input = document.createElement("input") as HTMLInputElement
                input.type = "file"
                input.accept = accept
                input.multiple = true
                input.onchange = {
                    val files = input.files
                    val count = files?.length ?: 0
                    val collected = mutableListOf<PickedFile>()
                    if (count == 0) {
                        // Cancelled: no callback, matching the contract on every other platform.
                    } else {
                        for (index in 0 until count) {
                            val file = files?.item(index) ?: continue
                            readFile(file) { picked ->
                                collected += picked
                                // Reads finish in arbitrary order; report once the last one lands.
                                if (collected.size == count) callback.value(collected.toList())
                            }
                        }
                    }
                    null
                }
                input.click()
            }

            override fun pickImages() = choose("image/*")
            override fun pickFiles() = choose("*/*")
        }
    }
}

private fun readFile(file: File, onRead: (PickedFile) -> Unit) {
    val reader = FileReader()
    reader.onload = {
        val buffer = reader.result as? ArrayBuffer
        if (buffer != null) {
            onRead(
                PickedFile(
                    fileName = file.name,
                    mimeType = file.type.takeIf { it.isNotBlank() },
                    bytes = buffer.toByteArray(),
                ),
            )
        }
        null
    }
    reader.readAsArrayBuffer(file)
}

private fun ArrayBuffer.toByteArray(): ByteArray {
    val view = Int8Array(this)
    return ByteArray(view.length) { view[it] }
}
