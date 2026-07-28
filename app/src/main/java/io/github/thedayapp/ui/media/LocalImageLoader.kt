package io.github.thedayapp.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import io.github.thedayapp.data.LocalImageReference
import io.github.thedayapp.media.LocalImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private data class LoadedLocalImage(
    val fileName: String,
    val maxDecodeLongEdgePx: Int,
    val bitmap: Bitmap,
)

@Composable
fun rememberLocalImageBitmap(
    image: LocalImageReference?,
    maxDecodeLongEdgePx: Int = 1280,
): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val imageStore = remember(context) {
        LocalImageStore(context)
    }

    val requestedFileName = image?.fileName
    val safeMaxDecodeLongEdgePx = maxDecodeLongEdgePx.coerceAtLeast(1)

    val loadedImage by produceState<LoadedLocalImage?>(
        initialValue = null,
        key1 = requestedFileName,
        key2 = safeMaxDecodeLongEdgePx,
    ) {
        if (requestedFileName == null) {
            value = null
            return@produceState
        }

        var decodedBitmap: Bitmap? = null

        try {
            val fileName = requestedFileName

            withContext(Dispatchers.IO) {
                val file = imageStore.fileFor(fileName)
                    ?: return@withContext

                decodedBitmap = decodeLocalImage(
                    file = file,
                    maxLongEdge = safeMaxDecodeLongEdgePx,
                )
            }

            val loadedBitmap = decodedBitmap
                ?: return@produceState

            value = LoadedLocalImage(
                fileName = fileName,
                maxDecodeLongEdgePx = safeMaxDecodeLongEdgePx,
                bitmap = loadedBitmap,
            )

            awaitCancellation()
        } finally {
            val bitmapToRecycle = decodedBitmap

            if (
                bitmapToRecycle != null &&
                value?.bitmap === bitmapToRecycle
            ) {
                value = null
            }

            if (
                bitmapToRecycle != null &&
                !bitmapToRecycle.isRecycled
            ) {
                bitmapToRecycle.recycle()
            }
        }
    }

    val matchingBitmap = loadedImage
        ?.takeIf { loaded ->
            loaded.fileName == requestedFileName &&
                loaded.maxDecodeLongEdgePx == safeMaxDecodeLongEdgePx
        }
        ?.bitmap

    return remember(
        matchingBitmap,
        requestedFileName,
        safeMaxDecodeLongEdgePx,
    ) {
        matchingBitmap?.asImageBitmap()
    }
}

internal fun localImageAlignment(
    image: LocalImageReference,
): Alignment {
    val safeFocusX = if (image.focusX.isFinite()) {
        image.focusX.coerceIn(0f, 1f)
    } else {
        0.5f
    }

    val safeFocusY = if (image.focusY.isFinite()) {
        image.focusY.coerceIn(0f, 1f)
    } else {
        0.5f
    }

    return BiasAlignment(
        horizontalBias = safeFocusX * 2f - 1f,
        verticalBias = safeFocusY * 2f - 1f,
    )
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxLongEdge: Int,
): Int {
    val longEdge = maxOf(width, height)
    var sampleSize = 1

    while (
        sampleSize <= Int.MAX_VALUE / 2 &&
        longEdge / (sampleSize * 2) >= maxLongEdge
    ) {
        sampleSize *= 2
    }

    return sampleSize
}

private fun decodeLocalImage(
    file: File,
    maxLongEdge: Int,
): Bitmap? {
    var sampledBitmap: Bitmap? = null

    try {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeFile(
            file.absolutePath,
            boundsOptions,
        )

        if (
            boundsOptions.outWidth <= 0 ||
            boundsOptions.outHeight <= 0
        ) {
            return null
        }

        val inSampleSize = calculateInSampleSize(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
            maxLongEdge = maxLongEdge,
        )

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        sampledBitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            decodeOptions,
        ) ?: return null

        val sampledLongEdge = maxOf(
            sampledBitmap.width,
            sampledBitmap.height,
        )

        if (sampledLongEdge <= maxLongEdge) {
            return sampledBitmap
        }

        val scale = maxLongEdge.toFloat() / sampledLongEdge.toFloat()

        val scaledWidth = maxOf(
            1,
            (sampledBitmap.width * scale).roundToInt(),
        )

        val scaledHeight = maxOf(
            1,
            (sampledBitmap.height * scale).roundToInt(),
        )

        val scaledBitmap = Bitmap.createScaledBitmap(
            sampledBitmap,
            scaledWidth,
            scaledHeight,
            true,
        )

        if (scaledBitmap !== sampledBitmap) {
            sampledBitmap.recycle()
            sampledBitmap = null
        }

        return scaledBitmap
    } catch (exception: Exception) {
        sampledBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        return null
    }
}