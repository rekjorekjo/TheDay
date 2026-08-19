package io.github.thedayapp.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
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
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private data class CachedLocalImage(
    val imageBitmap: ImageBitmap,
    val byteCount: Int,
)

/**
 * 在页面切换和 Lazy 列表回收后保留近期解码结果。图片文件名唯一，重新裁剪会得到新缓存键，
 * 因此不会错误复用旧像素。
 */
private object LocalImageMemoryCache {
    private const val ABSOLUTE_MAX_BYTES = 64 * 1024 * 1024

    private val maxBytes = minOf(
        ABSOLUTE_MAX_BYTES,
        (Runtime.getRuntime().maxMemory() / 12L)
            .coerceAtLeast(12L * 1024L * 1024L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt(),
    )

    private val cache = object : LruCache<String, CachedLocalImage>(maxBytes) {
        override fun sizeOf(key: String, value: CachedLocalImage): Int {
            return value.byteCount.coerceAtLeast(1)
        }
    }

    @Synchronized
    fun get(key: String): ImageBitmap? = cache.get(key)?.imageBitmap

    @Synchronized
    fun put(
        key: String,
        imageBitmap: ImageBitmap,
        byteCount: Int,
    ) {
        cache.put(
            key,
            CachedLocalImage(
                imageBitmap = imageBitmap,
                byteCount = byteCount,
            ),
        )
    }
}

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
    val cacheKey = requestedFileName?.let { fileName ->
        "$fileName@$safeMaxDecodeLongEdgePx"
    }

    val loadedImage by produceState<ImageBitmap?>(
        initialValue = cacheKey?.let(LocalImageMemoryCache::get),
        key1 = cacheKey,
    ) {
        if (requestedFileName == null || cacheKey == null) {
            value = null
            return@produceState
        }

        LocalImageMemoryCache.get(cacheKey)?.let { cached ->
            value = cached
            return@produceState
        }

        val decodedBitmap = withContext(Dispatchers.IO) {
            val file = imageStore.fileFor(requestedFileName)
                ?: return@withContext null

            decodeLocalImage(
                file = file,
                maxLongEdge = safeMaxDecodeLongEdgePx,
            )
        } ?: return@produceState

        val imageBitmap = decodedBitmap.asImageBitmap()
        val byteCount = runCatching {
            decodedBitmap.allocationByteCount
        }.getOrDefault(
            decodedBitmap.width * decodedBitmap.height * 4,
        )

        LocalImageMemoryCache.put(
            key = cacheKey,
            imageBitmap = imageBitmap,
            byteCount = byteCount,
        )
        value = imageBitmap
    }

    return loadedImage
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
        Log.w("TheDayImageLoader", "Failed to decode local image", exception)
        sampledBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        return null
    }
}
