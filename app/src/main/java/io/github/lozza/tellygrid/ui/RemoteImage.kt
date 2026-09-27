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
import java.io.File
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
    produceState<ImageBitmap?>(initialValue = null, key1 = url, key2 = maxDimension) {
        // Cache per size, so a small card image is never stretched across the banner.
        val key = url?.let { "$maxDimension|$it" }
        val cached = key?.let(RemoteImageCache::get)
        value = cached
        if (url.isNullOrBlank() || key == null || cached != null) return@produceState
        value = withContext(Dispatchers.IO) { loadImage(context, key, url, maxDimension) }
    }
    }

private fun loadImage(context: Context, key: String, uri: String, maxDimension: Int): ImageBitmap? {
    ImageDiskCache.read(context, key)?.let { bytes -> decodeImage(bytes, key, maxDimension)?.let { return it } }
    // Jellyfin's TV image provider fetches the full-size original and takes several seconds
    // per card; its public server URL returns a resized copy almost instantly.
    jellyfinSourceUrl(uri)?.let { source ->
        loadRemoteImage(key, "$source&fillWidth=${minOf(maxDimension * 2, 1920)}&quality=85", maxDimension, context)?.let { return it }
    }
    return loadImageUncached(context, key, uri, maxDimension)
}

private fun jellyfinSourceUrl(uri: String): String? {
    if (!uri.startsWith("content://org.jellyfin.androidtv.integration.provider.ImageProvider")) return null
    val source = runCatching { android.net.Uri.parse(uri).getQueryParameter("src") }.getOrNull()
    return source?.takeIf { it.startsWith("https://") && "/Images/" in it && '?' in it }
}

private fun loadImageUncached(context: Context, key: String, uri: String, maxDimension: Int): ImageBitmap? =
    if (uri.startsWith("asset:///")) {
        runCatching {
            context.assets.open(uri.removePrefix("asset:///"))
                .use { decodeImage(it.readBytes(), key, maxDimension) }
        }.getOrNull()
    } else if (uri.startsWith("content://") || uri.startsWith("android.resource://")) {
        runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                ?.use { it.readBytes() }
                ?.let { bytes -> decodeImage(bytes, key, maxDimension)?.also { ImageDiskCache.write(context, key, bytes) } }
        }.getOrNull()
    } else {
        loadRemoteImage(key, uri, maxDimension, context)
    }

/** Downloads [url] and caches it on disk under [cacheKey], so artwork survives app restarts. */
private fun loadRemoteImage(cacheKey: String, url: String, maxDimension: Int, context: Context): ImageBitmap? = runCatching {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "TellyGrid/0.4")
        setRequestProperty("Accept", "image/avif,image/webp,image/png,image/*")
    }

    try {
        val bytes = connection.inputStream.use { it.readBytes() }
        decodeImage(bytes, cacheKey, maxDimension)?.also { ImageDiskCache.write(context, cacheKey, bytes) }
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

private object ImageDiskCache {
    private const val MAX_BYTES = 60L * 1024 * 1024
    private const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

    private fun dir(context: Context) = File(context.cacheDir, "artwork").apply { mkdirs() }

    private fun file(context: Context, key: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(dir(context), digest.joinToString("") { "%02x".format(it) })
    }

    fun read(context: Context, key: String): ByteArray? = runCatching {
        file(context, key).takeIf { it.isFile && System.currentTimeMillis() - it.lastModified() < MAX_AGE_MILLIS }
            ?.readBytes()
    }.getOrNull()

    fun write(context: Context, key: String, bytes: ByteArray) {
        runCatching {
            val target = file(context, key)
            val temp = File(target.path + ".tmp")
            temp.writeBytes(bytes)
            temp.renameTo(target)
            trim(context)
        }
    }

    private fun trim(context: Context) {
        val files = dir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEach { f -> total += f.length(); if (total > MAX_BYTES) f.delete() }
    }
}

/**
 * A larger copy of well-known artwork CDN images, for the full-width banner. Apps often
 * publish thumbnail-sized URLs; these CDNs serve the same picture at other sizes by URL.
 * Returns null when there is no known larger version.
 */
internal fun largerArtworkUri(uri: String?): String? {
    if (uri == null) return null
    val upgraded = uri
        // Stremio: landscape backdrop instead of the small portrait poster.
        .replace(Regex("^(https://images\\.metahub\\.space)/poster/(small|medium)/"), "$1/background/large/")
        .replace(Regex("^(https://m\\.media-amazon\\.com/images/.+\\._V1_)[^/]*?(\\.jpg)$"), "$1SX1920$2")
        .replace(Regex("^(https://image\\.tmdb\\.org/t/p/)w\\d+/"), "$1w1280/")
        .replace(Regex("^(https://ichef\\.bbci\\.co\\.uk/images/ic/)\\d+x\\d+/"), "$11280x720/")
        .replace(Regex("([?&]w=)\\d{2,3}(&|$)"), "$11920$2")
        .replace(Regex("^(https://disney\\.images\\.edge\\.bamgrid\\.com/.+[?&])height=\\d+&width=\\d+"), "$1height=1080&width=1920")
        .replace(Regex("^(https://www\\.crunchyroll\\.com/imgsrv/display/thumbnail/)\\d+x\\d+/"), "$11280x720/")
        .replace(Regex("^(https://i\\.ytimg\\.com/vi/[^/]+/)(sd|hq|mq)default\\.jpg"), "$1maxresdefault.jpg")
    return upgraded.takeIf { it != uri }
}
