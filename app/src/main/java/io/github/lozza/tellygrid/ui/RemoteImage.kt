package io.github.lozza.tellygrid.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap

private object RemoteImageCache {
    private const val MAX_ENTRIES = 160
    private val images = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    fun get(key: String): ImageBitmap? = synchronized(images) { images[key] }

    fun put(key: String, image: ImageBitmap) {
        synchronized(images) { images[key] = image }
    }
}

@Composable
internal fun rememberRemoteImage(url: String?, maxDimension: Int = 960): State<ImageBitmap?> =
    LocalContext.current.applicationContext.let { context ->
    produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        val cached = url?.let(RemoteImageCache::get)
        value = cached
        if (url.isNullOrBlank() || cached != null) return@produceState
        value = withContext(Dispatchers.IO) { loadImage(context, url, maxDimension) }
    }
    }

private fun loadImage(context: Context, uri: String, maxDimension: Int): ImageBitmap? =
    if (uri.startsWith("asset:///")) {
        runCatching {
            context.assets.open(uri.removePrefix("asset:///"))
                .use { decodeImage(it.readBytes(), uri, maxDimension) }
        }.getOrNull()
    } else if (uri.startsWith("content://") || uri.startsWith("android.resource://")) {
        runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                ?.use { decodeImage(it.readBytes(), uri, maxDimension) }
        }.getOrNull()
    } else {
        loadRemoteImage(uri, maxDimension)
    }

private fun loadRemoteImage(url: String, maxDimension: Int): ImageBitmap? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "TellyGrid/0.4")
        setRequestProperty("Accept", "image/avif,image/webp,image/png,image/*")
    }

    try {
        connection.inputStream.use { stream ->
            decodeImage(stream.readBytes(), url, maxDimension)
        }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun decodeImage(bytes: ByteArray, cacheKey: String, maxDimension: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension * 2 ||
        bounds.outHeight / sampleSize > maxDimension * 2
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )?.asImageBitmap()?.also { RemoteImageCache.put(cacheKey, it) }
}
