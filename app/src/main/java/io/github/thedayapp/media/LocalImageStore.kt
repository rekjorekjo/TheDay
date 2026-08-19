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
        private const val MAX_CROPPED_LONG_EDGE = 3072
        private const val MAX_ORIGINAL_LONG_EDGE = 4096
        private const val WEBP_QUALITY = 88
    }

    suspend fun importImage(uri: Uri): Result<StoredImageFile> {
        return importImage(
            uri = uri,
            maxLongEdge = MAX_CROPPED_LONG_EDGE,
        )
    }

    suspend fun importOriginalImage(uri: Uri): Result<StoredImageFile> {
        return importImage(
            uri = uri,
            maxLongEdge = MAX_ORIGINAL_LONG_EDGE,
        )
    }

    private suspend fun importImage(
        uri: Uri,
        maxLongEdge: Int,
    ): Result<StoredImageFile> {
        var importedFileName: String? = null

        return try {
            withContext(Dispatchers.IO) {
                try {
                    val storedImage = doImportImage(
                        uri = uri,
                        maxLongEdge = maxLongEdge,
                    )

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
                    }.onFailure { cleanupError ->
                        Log.w(TAG, "Failed to clean up cancelled image import", cleanupError)
                    }
                }
            }

            throw exception
        }
    }

    private fun doImportImage(
        uri: Uri,
        maxLongEdge: Int,
    ): StoredImageFile {
        ensureDirectoryExists()

        var sourceTempFile: File? = null

        val mimeType = appContext.contentResolver.getType(uri)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            Log.w(
                TAG,
                "Picker returned a non-image MIME type; attempting decode",
            )
        }

        // 先复制到应用私有临时文件，后续尺寸读取、EXIF 和解码都基于同一稳定来源。
        sourceTempFile = File(
            imageDirectory,
            ".source-${UUID.randomUUID()}.tmp",
        )

        var bitmap: Bitmap? = null
        var tempFile: File? = null
        var finalFile: File? = null
        var importCompleted = false

        try {
            copyUriToSourceFile(
                uri = uri,
                destination = sourceTempFile,
            )

            val (originalWidth, originalHeight) = readImageBounds(sourceTempFile)
            if (originalWidth <= 0 || originalHeight <= 0) {
                throw IOException("Invalid image dimensions")
            }

            // 先按尺寸采样，再应用 EXIF 方向并限制最长边，降低大图导入时的峰值内存。
            val inSampleSize = calculateInSampleSize(
                width = originalWidth,
                height = originalHeight,
                maxLongEdge = maxLongEdge,
            )

            bitmap = decodeBitmap(sourceTempFile, inSampleSize)
                ?: throw IOException("Failed to decode bitmap")

            val exifOrientation = readExifOrientation(sourceTempFile)

            val orientedBitmap = applyExifOrientation(bitmap, exifOrientation)
            if (orientedBitmap !== bitmap) {
                bitmap.recycle()
                bitmap = orientedBitmap
            }

            val scaledBitmap = scaleIfNeeded(
                bitmap = bitmap,
                maxLongEdge = maxLongEdge,
            )
            if (scaledBitmap !== bitmap) {
                bitmap.recycle()
                bitmap = scaledBitmap
            }

            // 先写临时 WebP，确认文件有效后再移动到最终文件名，失败时统一清理中间产物。
            val fileName = "${UUID.randomUUID()}.webp"
            val outputFile = File(imageDirectory, fileName)
            val temporaryOutputFile = File(imageDirectory, ".$fileName.tmp")
            finalFile = outputFile
            tempFile = temporaryOutputFile

            writeWebP(bitmap, temporaryOutputFile)

            if (!temporaryOutputFile.renameTo(outputFile)) {
                temporaryOutputFile.copyTo(outputFile, overwrite = true)
                temporaryOutputFile.delete()
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IOException("Failed to write final file")
            }

            val storedImage = StoredImageFile(
                fileName = outputFile.name,
                width = bitmap.width,
                height = bitmap.height,
            )

            importCompleted = true
            return storedImage
        } finally {
            // 无论成功与否都清理临时文件并回收 Bitmap；失败时同时删除未完成的目标文件。
            sourceTempFile?.delete()

            tempFile?.delete()

            if (!importCompleted) {
                finalFile?.delete()
            }

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
        } catch (exception: IOException) {
            Log.w(TAG, "Failed to read EXIF orientation; using default", exception)
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

    private fun scaleIfNeeded(
        bitmap: Bitmap,
        maxLongEdge: Int,
    ): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdge) {
            return bitmap
        }

        val scale = maxLongEdge.toFloat() / longEdge
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