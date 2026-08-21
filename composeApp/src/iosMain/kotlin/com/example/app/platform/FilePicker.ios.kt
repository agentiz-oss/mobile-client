package com.example.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * Two native pickers, matching what each entry point means on iOS.
 *
 * Photos go through `PHPickerViewController`, which runs **out of process**: it hands back only
 * what the user chose and therefore needs no `NSPhotoLibraryUsageDescription` and shows no
 * permission prompt at all. Files go through `UIDocumentPickerViewController`, which is likewise
 * permissionless — the user granting access *is* the pick. That is why this target adds no
 * Info.plist keys; anything using the older `UIImagePickerController` would have needed both.
 *
 * Both controllers keep their delegate as a **weak** reference, so the delegates below are held in
 * a property of the launcher until they answer. Without that they are collected the moment the
 * picker appears and the callback never arrives.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(onPicked: (List<PickedFile>) -> Unit): FilePickerLauncher {
    val callback = rememberUpdatedState(onPicked)

    return remember {
        object : FilePickerLauncher {
            /** Alive only while a picker is on screen; see the note about weak delegates above. */
            private var retainedDelegate: NSObject? = null

            private fun finish(files: List<PickedFile>) {
                retainedDelegate = null
                if (files.isNotEmpty()) callback.value(files)
            }

            override fun pickImages() {
                val configuration = PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    // 0 means "no limit" in PhotosUI; the server caps a task at 100 attachments.
                    selectionLimit = 0
                }
                val controller = PHPickerViewController(configuration)
                val delegate = PhotoPickerDelegate(::finish)
                retainedDelegate = delegate
                controller.delegate = delegate
                present(controller)
            }

            override fun pickFiles() {
                val controller = UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(UTTypeItem),
                    asCopy = true,
                )
                controller.allowsMultipleSelection = true
                val delegate = DocumentPickerDelegate(::finish)
                retainedDelegate = delegate
                controller.delegate = delegate
                present(controller)
            }
        }
    }
}

/** The controller the app is actually showing — Compose lives inside it, so it can present. */
private fun present(controller: UIViewController) {
    dispatch_async(dispatch_get_main_queue()) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@dispatch_async
        // A screen that already presented something (a share sheet) would otherwise silently fail.
        var top = root
        while (true) top = top.presentedViewController ?: break
        top.presentViewController(controller, animated = true, completion = null)
    }
}

/**
 * Collects the chosen photos. `loadDataRepresentation` is asynchronous and per item, so results
 * arrive out of order and the completion fires once the last one has answered.
 */
@OptIn(ExperimentalForeignApi::class)
private class PhotoPickerDelegate(
    private val onDone: (List<PickedFile>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            onDone(emptyList())
            return
        }
        val collected = mutableListOf<PickedFile>()
        var remaining = results.size
        results.forEach { result ->
            val provider: NSItemProvider = result.itemProvider
            // The type the photo is stored as; a HEIC stays HEIC, which the server passes through
            // and the app's own viewer can render on this platform.
            val identifier = provider.registeredTypeIdentifiers.firstOrNull() as? String ?: "public.image"
            provider.loadDataRepresentationForTypeIdentifier(identifier) { data, _ ->
                dispatch_async(dispatch_get_main_queue()) {
                    data?.toByteArray()?.let { bytes ->
                        collected += PickedFile(
                            fileName = provider.suggestedName?.let { "$it.${extensionFor(identifier)}" }
                                ?: "photo.${extensionFor(identifier)}",
                            mimeType = mimeFor(identifier),
                            bytes = bytes,
                        )
                    }
                    remaining -= 1
                    if (remaining == 0) onDone(collected.toList())
                }
            }
        }
    }
}

/** Documents come back as copies in the app's own sandbox (`asCopy = true`), so a plain read works. */
@OptIn(ExperimentalForeignApi::class)
private class DocumentPickerDelegate(
    private val onDone: (List<PickedFile>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
        onDone(
            urls.mapNotNull { url ->
                val bytes = NSData.dataWithContentsOfURL(url)?.toByteArray() ?: return@mapNotNull null
                PickedFile(fileName = url.lastPathComponent ?: "file", mimeType = null, bytes = bytes)
            },
        )
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDone(emptyList())
    }
}

/** Only what PhotosUI actually hands back; anything else keeps the generic name and no MIME type. */
private fun extensionFor(identifier: String): String = when (identifier) {
    "public.png" -> "png"
    "public.heic" -> "heic"
    "com.compuserve.gif" -> "gif"
    "org.webmproject.webp", "public.webp" -> "webp"
    else -> "jpg"
}

private fun mimeFor(identifier: String): String = when (identifier) {
    "public.png" -> "image/png"
    "public.heic" -> "image/heic"
    "com.compuserve.gif" -> "image/gif"
    "org.webmproject.webp", "public.webp" -> "image/webp"
    else -> "image/jpeg"
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out
}
