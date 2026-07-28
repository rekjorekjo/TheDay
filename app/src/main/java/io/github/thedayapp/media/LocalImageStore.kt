package io.github.thedayapp.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

data class StoredImageFile(
    val fileName: String,
    val width: Int,
    val height: Int,
)

class LocalImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val imageDirectory = File(appContext.filesDir, "images")

    companion object {
        private const val TAG = "TheDayImageStore"
        private const val MAX_LONG_EDGE = 2048
        private const val WEBP_QUALITY = 88
    }

    suspend fun importImage(uri: Uri): Result<StoredImageFile> {
        var importedFileName: String? = null

        return try {
            withContext(Dispatchers.IO) {
                try {
                    val storedImage = doImportImage(uri)

                    importedFileName = storedImage.fileName

                    Result.success(storedImage)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(
                        TAG,
                        "Local image import failed",
                        exception,
                    )
                    Result.failure(exception)
                }
            }
        } catch (exception: CancellationException) {
            val fileName = importedFileName

            if (fileName != null) {
                withContext(
                    NonCancellable +
                        Dispatchers.IO,
                ) {
                    runCatching {
                        deleteImage(fileName)
                    }
                }
            }

            throw exception
        }
    }

    private fun doImportImage(uri: Uri): StoredImageFile {
        ensureDirectoryExists()

        var sourceTempFile: File? = null

        val mimeType = appContext.contentResolver.getType(uri)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            Log.w(
                TAG,
                "Picker returned a non-image MIME type; attempting decode",
            )
        }

        // Create temporary source file
        sourceTempFile = File(
            imageDirectory,
            ".source-${UUID.randomUUID()}.tmp",
        )

        var bitmap: Bitmap? = null
        var tempFile: File? = null
        var finalFile: File? = null
        var importCompleted = false

        try {
            // Copy URI content to temporary source file
            copyUriToSourceFile(
                uri = uri,
                destination = sourceTempFile,
            )

            // Read bounds from temporary source file
            val (originalWidth, originalHeight) = readImageBounds(sourceTempFile)
            if (originalWidth <= 0 || originalHeight <= 0) {
                throw IOException("Invalid image dimensions")
            }

            // Calculate sample size
            val inSampleSize = calculateInSampleSize(originalWidth, originalHeight, MAX_LONG_EDGE)

            // Decode bitmap from temporary source file
            bitmap = decodeBitmap(sourceTempFile, inSampleSize)
                ?: throw IOException("Failed to decode bitmap")

            // Read EXIF orientation from temporary source file
            val exifOrientation = readExifOrientation(sourceTempFile)

            // Apply EXIF orientation (may return same or new bitmap)
            val orientedBitmap = applyExifOrientation(bitmap, exifOrientation)
            if (orientedBitmap !== bitmap) {
                bitmap.recycle()
                bitmap = orientedBitmap
            }

            // Scale if needed (may return same or new bitmap)
            val scaledBitmap = scaleIfNeeded(bitmap)
            if (scaledBitmap !== bitmap) {
                bitmap.recycle()
                bitmap = scaledBitmap
            }

            // Generate file name
            val fileName = "${UUID.randomUUID()}.webp"
            finalFile = File(imageDirectory, fileName)
            tempFile = File(imageDirectory, ".$fileName.tmp")

            // Write to temp file
            writeWebP(bitmap, tempFile)

            // Move to final location
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            // Verify
            if (!finalFile.exists() || finalFile.length() == 0L) {
                throw IOException("Failed to write final file")
            }

            val result = StoredImageFile(
                fileName = finalFile.name,
                width = bitmap.width,
                height = bitmap.height,
            )

            importCompleted = true
            return result
        } finally {
            // Clean up temporary source file
            sourceTempFile?.delete()

            // Clean up temp file
            tempFile?.delete()

            // Clean up final file on failure
            if (!importCompleted) {
                finalFile?.delete()
            }

            // Recycle bitmap
            bitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
        }
    }

    private fun ensureDirectoryExists() {
        if (imageDirectory.exists()) {
            if (!imageDirectory.isDirectory) {
                throw IOException("Image path is not a directory")
            }
            return
        }

        if (!imageDirectory.mkdirs() && !imageDirectory.isDirectory) {
            throw IOException("Failed to create image directory")
        }
    }

    private fun copyUriToSourceFile(
        uri: Uri,
        destination: File,
    ) {
        val inputStream = appContext.contentResolver
            .openInputStream(uri)
            ?: throw IOException("Failed to open selected image")

        inputStream.use { input ->
            destination.outputStream()
                .buffered()
                .use { output ->
                    input.copyTo(output)
                    output.flush()
                }
        }

        if (!destination.exists() || destination.length() <= 0L) {
            throw IOException("Selected image is empty")
        }
    }

    private fun readImageBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeFile(
            file.absolutePath,
            options,
        )

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("Invalid image dimensions")
        }

        return Pair(options.outWidth, options.outHeight)
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxLongEdge: Int,
    ): Int {
        val longEdge = maxOf(width, height)
        var sampleSize = 1

        while (longEdge / (sampleSize * 2) >= maxLongEdge) {
            sampleSize *= 2
        }

        return sampleSize
    }

    private fun decodeBitmap(file: File, inSampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            options,
        )
    }

    private fun readExifOrientation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.setScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.setRotate(180f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setScale(1f, -1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.setRotate(90f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.setRotate(-90f)
            }
            else -> return bitmap
        }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
    }

    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_LONG_EDGE) {
            return bitmap
        }

        val scale = MAX_LONG_EDGE.toFloat() / longEdge
        val newWidth = maxOf(1, (bitmap.width * scale).roundToInt())
        val newHeight = maxOf(1, (bitmap.height * scale).roundToInt())

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    @Suppress("DEPRECATION")
    private fun writeWebP(bitmap: Bitmap, file: File) {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.hasAlpha()) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.WEBP_LOSSY
            }
        } else {
            Bitmap.CompressFormat.WEBP
        }

        file.outputStream().use { outputStream ->
            val success = bitmap.compress(format, WEBP_QUALITY, outputStream)
            if (!success) {
                throw IOException("Failed to compress bitmap")
            }
            outputStream.flush()
        }
    }

    private fun resolveSafeImageFile(fileName: String): File? {
        if (fileName.isEmpty()) return null
        if (File(fileName).name != fileName) return null
        if (!fileName.endsWith(".webp")) return null

        return try {
            val directory = imageDirectory.canonicalFile
            val candidate = File(directory, fileName).canonicalFile

            if (candidate.parentFile != directory) {
                null
            } else {
                candidate
            }
        } catch (_: IOException) {
            null
        }
    }

    fun fileFor(fileName: String): File? {
        val file = resolveSafeImageFile(fileName) ?: return null

        return file.takeIf {
            it.exists() && it.isFile
        }
    }

    fun imageExists(fileName: String): Boolean {
        return fileFor(fileName) != null
    }

    fun deleteImage(fileName: String): Boolean {
        val file = resolveSafeImageFile(fileName) ?: return false

        if (!file.exists()) {
            return true
        }

        if (!file.isFile) {
            return false
        }

        return file.delete()
    }
}