package io.github.thedayapp.sharing

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.thedayapp.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

object EventShareActions {
    private const val SHARED_IMAGE_DIRECTORY = "shared_images"
    private const val SHARED_IMAGE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L

    suspend fun shareEventImage(
        context: Context,
        bitmap: Bitmap,
    ): Result<Unit> {
        return try {
            val sharedFile = withContext(Dispatchers.IO) {
                createSharedImageFile(context, bitmap)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sharedFile,
            )

            withContext(Dispatchers.Main) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    clipData = ClipData.newRawUri("The Day", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                launchChooser(
                    context = context,
                    chooser = Intent.createChooser(
                        sendIntent,
                        context.getString(R.string.share_memory_image_chooser),
                    ).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun shareImages(
        context: Context,
        bitmaps: List<Bitmap>,
    ): Result<Unit> {
        return try {
            if (bitmaps.isEmpty()) {
                throw IOException("No images to share")
            }

            val sharedFiles = withContext(Dispatchers.IO) {
                bitmaps.map { bitmap ->
                    createSharedImageFile(context, bitmap)
                }
            }

            val contentUris = ArrayList<Uri>(sharedFiles.size)
            sharedFiles.forEach { file ->
                contentUris += FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }

            withContext(Dispatchers.Main) {
                if (contentUris.size == 1) {
                    val contentUri = contentUris.first()
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        clipData = ClipData.newRawUri("The Day", contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    launchChooser(
                        context = context,
                        chooser = Intent.createChooser(
                            sendIntent,
                            context.getString(R.string.share_memory_image_chooser),
                        ).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                } else {
                    val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/png"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(contentUris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(context.contentResolver, "The Day", contentUris.first())
                        contentUris.drop(1).forEach { uri ->
                            clipData?.addItem(ClipData.Item(uri))
                        }
                    }
                    launchChooser(
                        context = context,
                        chooser = Intent.createChooser(
                            sendIntent,
                            context.getString(R.string.share_memory_image_chooser),
                        ).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                    )
                }
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun saveImageToGallery(
        context: Context,
        bitmap: Bitmap,
    ): Result<Uri> {
        return try {
            val uri = withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveImageWithMediaStore(context, bitmap)
                } else {
                    saveImageLegacy(context, bitmap)
                }
            }

            Result.success(uri)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun saveImageWithMediaStore(
        context: Context,
        bitmap: Bitmap,
    ): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "TheDay-${System.currentTimeMillis()}.png",
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/The Day",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("Failed to create gallery entry")

        try {
            resolver.openOutputStream(uri, "w")
                ?.buffered()
                ?.use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IOException("Failed to encode gallery image")
                    }
                    output.flush()
                }
                ?: throw IOException("Failed to open gallery output")

            val publishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }

            if (resolver.update(uri, publishValues, null, null) <= 0) {
                throw IOException("Failed to publish gallery image")
            }

            return uri
        } catch (exception: Exception) {
            runCatching {
                resolver.delete(uri, null, null)
            }
            throw exception
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveImageLegacy(
        context: Context,
        bitmap: Bitmap,
    ): Uri {
        val picturesDirectory = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES,
        )
        val outputDirectory = File(picturesDirectory, "The Day")

        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IOException("Failed to create gallery directory")
        }

        if (!outputDirectory.isDirectory) {
            throw IOException("Gallery path is not a directory")
        }

        val outputFile = File(
            outputDirectory,
            "TheDay-${System.currentTimeMillis()}.png",
        )

        try {
            outputFile.outputStream()
                .buffered()
                .use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IOException("Failed to encode gallery image")
                    }
                    output.flush()
                }

            if (!outputFile.exists() || outputFile.length() <= 0L) {
                throw IOException("Gallery image is empty")
            }

            return suspendCancellableCoroutine { continuation ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outputFile.absolutePath),
                    arrayOf("image/png"),
                ) { _, scannedUri ->
                    if (continuation.isActive) {
                        continuation.resume(
                            scannedUri ?: Uri.fromFile(outputFile),
                        )
                    }
                }
            }
        } catch (exception: Exception) {
            outputFile.delete()
            throw exception
        }
    }

    private fun createSharedImageFile(
        context: Context,
        bitmap: Bitmap,
    ): File {
        val directory = File(
            context.cacheDir,
            SHARED_IMAGE_DIRECTORY,
        )

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Failed to create shared image directory")
        }

        if (!directory.isDirectory) {
            throw IOException("Shared image path is not a directory")
        }

        val expiry = System.currentTimeMillis() - SHARED_IMAGE_MAX_AGE_MILLIS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < expiry) {
                file.delete()
            }
        }

        val outputFile = File(
            directory,
            "the-day-share-${System.currentTimeMillis()}-${(0..9999).random()}.png",
        )

        outputFile.outputStream()
            .buffered()
            .use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Failed to encode shared image")
                }
                output.flush()
            }

        if (!outputFile.exists() || outputFile.length() <= 0L) {
            throw IOException("Shared image is empty")
        }

        return outputFile
    }

    private fun launchChooser(
        context: Context,
        chooser: Intent,
    ) {
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
