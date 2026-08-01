package io.github.thedayapp.ui.media

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yalantis.ucrop.UCrop
import io.github.thedayapp.R
import io.github.thedayapp.data.LocalImageReference
import io.github.thedayapp.media.LocalImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

private const val CROP_CACHE_DIRECTORY = "ucrop"
private const val CROP_FILE_PREFIX = "crop-"
private const val SOURCE_FILE_PREFIX = "source-"
private const val CROP_FILE_SUFFIX = ".png"
private const val SOURCE_FILE_SUFFIX = ".source"
private const val CROP_MAX_RESULT_SIZE = 2048
private const val STALE_CROP_FILE_AGE_MILLIS = 24L * 60L * 60L * 1000L

data class CroppedImageSelection(
    val originalUri: Uri,
    val croppedUri: Uri,
)

/**
 * Opens the system photo picker, retains a cached source, and immediately
 * launches uCrop. Leaving the default full-image crop unchanged keeps the
 * complete composition while still producing the display copy used by the UI.
 */
@Composable
fun rememberSingleImagePickerLauncher(
    onImagePicked: (CroppedImageSelection) -> Unit,
    onCropFailed: (Throwable?) -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val cropTitle = stringResource(R.string.crop_image)
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()

    val currentOnImagePicked by rememberUpdatedState(onImagePicked)
    val currentOnCropFailed by rememberUpdatedState(onCropFailed)

    var pendingSourceUriString by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var pendingCropUriString by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    fun consumePendingUris(): Pair<Uri?, Uri?> {
        val source = pendingSourceUriString?.let(Uri::parse)
        val crop = pendingCropUriString?.let(Uri::parse)
        pendingSourceUriString = null
        pendingCropUriString = null
        return source to crop
    }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val (sourceUri, pendingCropUri) = consumePendingUris()

        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val resultUri = result.data
                    ?.let { data -> UCrop.getOutput(data) }
                    ?: pendingCropUri

                if (sourceUri == null || resultUri == null) {
                    sourceUri?.let { deleteTemporaryPickerImage(context, it) }
                    resultUri?.let { deleteTemporaryPickerImage(context, it) }
                    currentOnCropFailed(
                        IOException("uCrop returned an incomplete result"),
                    )
                } else {
                    if (pendingCropUri != null && pendingCropUri != resultUri) {
                        deleteTemporaryPickerImage(context, pendingCropUri)
                    }

                    try {
                        currentOnImagePicked(
                            CroppedImageSelection(
                                originalUri = sourceUri,
                                croppedUri = resultUri,
                            ),
                        )
                    } catch (exception: Exception) {
                        deleteTemporaryPickerImage(context, sourceUri)
                        deleteTemporaryPickerImage(context, resultUri)
                        currentOnCropFailed(exception)
                    }
                }
            }

            UCrop.RESULT_ERROR -> {
                sourceUri?.let { deleteTemporaryPickerImage(context, it) }
                pendingCropUri?.let { deleteTemporaryPickerImage(context, it) }

                val error = result.data
                    ?.let { data -> UCrop.getError(data) }
                currentOnCropFailed(error)
            }

            else -> {
                sourceUri?.let { deleteTemporaryPickerImage(context, it) }
                pendingCropUri?.let { deleteTemporaryPickerImage(context, it) }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { selectedUri ->
        if (selectedUri == null) {
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            val preparedUris: Pair<Uri, Uri> = try {
                withContext(Dispatchers.IO) {
                    val sourceUri = createTemporarySourceUri(
                        context = context,
                        selectedUri = selectedUri,
                    )
                    val cropUri = try {
                        createTemporaryCropUri(context)
                    } catch (exception: Exception) {
                        deleteTemporaryPickerImage(context, sourceUri)
                        throw exception
                    }
                    sourceUri to cropUri
                }
            } catch (exception: Exception) {
                currentOnCropFailed(exception)
                return@launch
            }

            val (sourceUri, cropUri) = preparedUris
            pendingSourceUriString = sourceUri.toString()
            pendingCropUriString = cropUri.toString()

            val cropIntent = createCropIntent(
                context = context,
                sourceUri = sourceUri,
                destinationUri = cropUri,
                cropTitle = cropTitle,
                toolbarColor = colorScheme.surface.toArgb(),
                toolbarWidgetColor = colorScheme.onSurface.toArgb(),
                activeControlsColor = colorScheme.primary.toArgb(),
                rootBackgroundColor = colorScheme.background.toArgb(),
                dimmedLayerColor = colorScheme.scrim.copy(alpha = 0.68f).toArgb(),
                cropFrameColor = colorScheme.primary.toArgb(),
                cropGridColor = colorScheme.onSurface.copy(alpha = 0.72f).toArgb(),
            )

            try {
                cropLauncher.launch(cropIntent)
            } catch (exception: Exception) {
                pendingSourceUriString = null
                pendingCropUriString = null
                deleteTemporaryPickerImage(context, sourceUri)
                deleteTemporaryPickerImage(context, cropUri)
                currentOnCropFailed(exception)
            }
        }
    }

    return {
        pendingSourceUriString
            ?.let(Uri::parse)
            ?.let { deleteTemporaryPickerImage(context, it) }
        pendingCropUriString
            ?.let(Uri::parse)
            ?.let { deleteTemporaryPickerImage(context, it) }
        pendingSourceUriString = null
        pendingCropUriString = null

        photoPickerLauncher.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }
}

/**
 * Reopens uCrop from the persisted uncropped source. Pressing uCrop's check
 * button without changing the full-image crop keeps the complete source.
 */
@Composable
fun rememberImageRecropLauncher(
    image: LocalImageReference?,
    onImageCropped: (Uri) -> Unit,
    onCropFailed: (Throwable?) -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val cropTitle = stringResource(R.string.recrop_image)
    val colorScheme = MaterialTheme.colorScheme

    val currentImage by rememberUpdatedState(image)
    val currentOnImageCropped by rememberUpdatedState(onImageCropped)
    val currentOnCropFailed by rememberUpdatedState(onCropFailed)

    var pendingCropUriString by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pendingCropUri = pendingCropUriString?.let(Uri::parse)
        pendingCropUriString = null

        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val resultUri = result.data
                    ?.let { data -> UCrop.getOutput(data) }
                    ?: pendingCropUri

                if (resultUri == null) {
                    currentOnCropFailed(
                        IOException("uCrop returned no output URI"),
                    )
                } else {
                    if (pendingCropUri != null && pendingCropUri != resultUri) {
                        deleteTemporaryPickerImage(context, pendingCropUri)
                    }

                    try {
                        currentOnImageCropped(resultUri)
                    } catch (exception: Exception) {
                        deleteTemporaryPickerImage(context, resultUri)
                        currentOnCropFailed(exception)
                    }
                }
            }

            UCrop.RESULT_ERROR -> {
                pendingCropUri?.let { deleteTemporaryPickerImage(context, it) }
                currentOnCropFailed(
                    result.data?.let { data -> UCrop.getError(data) },
                )
            }

            else -> {
                pendingCropUri?.let { deleteTemporaryPickerImage(context, it) }
            }
        }
    }

    fun currentSourceFile(): File? {
        val current = currentImage ?: return null
        val sourceFileName = current.originalFileName ?: current.fileName
        return LocalImageStore(context).fileFor(sourceFileName)
    }

    fun launchRecrop() {
        pendingCropUriString
            ?.let(Uri::parse)
            ?.let { deleteTemporaryPickerImage(context, it) }
        pendingCropUriString = null

        val sourceFile = currentSourceFile()
        if (sourceFile == null) {
            currentOnCropFailed(IOException("The original image is missing"))
            return
        }

        val destinationUri = try {
            createTemporaryCropUri(context)
        } catch (exception: Exception) {
            currentOnCropFailed(exception)
            return
        }
        pendingCropUriString = destinationUri.toString()

        val cropIntent = createCropIntent(
            context = context,
            sourceUri = Uri.fromFile(sourceFile),
            destinationUri = destinationUri,
            cropTitle = cropTitle,
            toolbarColor = colorScheme.surface.toArgb(),
            toolbarWidgetColor = colorScheme.onSurface.toArgb(),
            activeControlsColor = colorScheme.primary.toArgb(),
            rootBackgroundColor = colorScheme.background.toArgb(),
            dimmedLayerColor = colorScheme.scrim.copy(alpha = 0.68f).toArgb(),
            cropFrameColor = colorScheme.primary.toArgb(),
            cropGridColor = colorScheme.onSurface.copy(alpha = 0.72f).toArgb(),
        )

        try {
            cropLauncher.launch(cropIntent)
        } catch (exception: Exception) {
            pendingCropUriString = null
            deleteTemporaryPickerImage(context, destinationUri)
            currentOnCropFailed(exception)
        }
    }

    return recrop@{
        if (currentImage == null) {
            currentOnCropFailed(IOException("No image is available to crop"))
            return@recrop
        }
        if (currentSourceFile() == null) {
            currentOnCropFailed(IOException("The original image is missing"))
            return@recrop
        }
        launchRecrop()
    }
}

private fun createCropIntent(
    context: Context,
    sourceUri: Uri,
    destinationUri: Uri,
    cropTitle: String,
    toolbarColor: Int,
    toolbarWidgetColor: Int,
    activeControlsColor: Int,
    rootBackgroundColor: Int,
    dimmedLayerColor: Int,
    cropFrameColor: Int,
    cropGridColor: Int,
) = UCrop.of(
    sourceUri,
    destinationUri,
)
    .withMaxResultSize(
        CROP_MAX_RESULT_SIZE,
        CROP_MAX_RESULT_SIZE,
    )
    .withOptions(
        UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setHideBottomControls(false)
            setCompressionFormat(Bitmap.CompressFormat.PNG)
            setToolbarTitle(cropTitle)
            setToolbarColor(toolbarColor)
            setToolbarWidgetColor(toolbarWidgetColor)
            setActiveControlsWidgetColor(activeControlsColor)
            setRootViewBackgroundColor(rootBackgroundColor)
            setDimmedLayerColor(dimmedLayerColor)
            setCropFrameColor(cropFrameColor)
            setCropGridColor(cropGridColor)
        },
    )
    .getIntent(context)
    .apply {
        setClass(
            context,
            EdgeHandleUCropActivity::class.java,
        )
    }

private fun createTemporarySourceUri(
    context: Context,
    selectedUri: Uri,
): Uri {
    val directory = cropCacheDirectory(context)
    ensureCropCacheDirectory(directory)
    deleteStalePickerImages(directory)

    val sourceFile = File(
        directory,
        "$SOURCE_FILE_PREFIX${UUID.randomUUID()}$SOURCE_FILE_SUFFIX",
    )

    val inputStream = context.contentResolver.openInputStream(selectedUri)
        ?: throw IOException("Failed to open selected image")

    try {
        inputStream.use { input ->
            sourceFile.outputStream().buffered().use { output ->
                input.copyTo(output)
                output.flush()
            }
        }

        if (!sourceFile.isFile || sourceFile.length() <= 0L) {
            throw IOException("Selected image is empty")
        }

        return Uri.fromFile(sourceFile)
    } catch (exception: Exception) {
        sourceFile.delete()
        throw exception
    }
}

private fun createTemporaryCropUri(context: Context): Uri {
    val directory = cropCacheDirectory(context)
    ensureCropCacheDirectory(directory)
    deleteStalePickerImages(directory)

    val outputFile = File(
        directory,
        "$CROP_FILE_PREFIX${UUID.randomUUID()}$CROP_FILE_SUFFIX",
    )

    return Uri.fromFile(outputFile)
}

private fun ensureCropCacheDirectory(directory: File) {
    if (!directory.exists() && !directory.mkdirs()) {
        throw IOException("Failed to create the uCrop cache directory")
    }

    if (!directory.isDirectory) {
        throw IOException("The uCrop cache path is not a directory")
    }
}

private fun deleteStalePickerImages(directory: File) {
    val staleBefore = System.currentTimeMillis() - STALE_CROP_FILE_AGE_MILLIS

    runCatching {
        directory.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    isTemporaryPickerFile(file) &&
                    file.lastModified() < staleBefore
            }
            ?.forEach { file ->
                runCatching { file.delete() }
            }
    }
}

private fun cropCacheDirectory(context: Context): File {
    return File(context.cacheDir, CROP_CACHE_DIRECTORY)
}

private fun isTemporaryPickerFile(file: File): Boolean {
    return file.name.startsWith(CROP_FILE_PREFIX) ||
        file.name.startsWith(SOURCE_FILE_PREFIX)
}

/** Deletes only files created in this app's cache/ucrop directory. */
internal fun deleteTemporaryPickerImage(
    context: Context,
    uri: Uri,
) {
    if (uri.scheme != ContentResolver.SCHEME_FILE) {
        return
    }

    val path = uri.path ?: return

    runCatching {
        val cropDirectory = cropCacheDirectory(context).canonicalFile
        val file = File(path).canonicalFile

        if (
            file.parentFile == cropDirectory &&
            isTemporaryPickerFile(file)
        ) {
            file.delete()
        }
    }
}
