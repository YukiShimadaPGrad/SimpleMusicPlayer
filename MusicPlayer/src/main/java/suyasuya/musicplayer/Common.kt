package suyasuya.musicplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.media.session.MediaSession
import android.media.session.MediaSession.QueueItem
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import java.io.Serializable
import kotlin.math.roundToInt
import kotlin.reflect.KClass


//private const val TAG = "suyasuya.musicplayer"

val <T : Any> KClass<T>.qualifiedNameWithoutCompanion: String?
    get() = this.qualifiedName.let {
        it?.replace(Regex("\\.Companion\$"), "")
    }

val MediaSession.queue: MutableList<QueueItem>?
    get() = controller.queue

val MediaSession.queueIndex: Long?
    get() = controller.playbackState?.activeQueueItemId

val MediaSession.currentQueueItem: QueueItem?
    get() = queue?.getOrNull(queueIndex?.toInt() ?: -1)

fun Long.toTimeString(): String = String.format("%1\$tM:%1\$tS", this)  // "%1\$tH:%1\$tM:%1\$tS.%1\$tL"


fun <T : Parcelable> Intent.getParcelableExtraCMP(name: String, clazz: KClass<T>): T? {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("Deprecation")
        getParcelableExtra(name)
    } else {
        getParcelableExtra(name, clazz.java)
    }
}

fun <T : Serializable> Intent.getSerializableExtraCMP(name: String, clazz: KClass<T>): T? {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        getSerializableExtra(name) as T?
    } else {
        getSerializableExtra(name, clazz.java)
    }
}

fun <T : Parcelable> Bundle.getParcelableCMP(name: String, clazz: KClass<T>): T? {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("Deprecation")
        getParcelable(name)
    } else {
        getParcelable(name, clazz.java)
    }
}

fun <T : Parcelable> Bundle.getParcelableArrayListCMP(name: String, clazz: KClass<T>): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("Deprecation")
        getParcelableArrayList(name)
    } else {
        getParcelableArrayList(name, clazz.java)
    }
}

@Deprecated("", ReplaceWith("Uri.getAlbumArtBitmapOrElse(contentResolver: ContentResolver, size){}"))
///** Uriの画像を可能ならバージョンごとに適した手段で取得する。 */
fun Uri.getBitmapOrNull(contentResolver: ContentResolver): Bitmap? {
    return kotlin.runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, this)
        } else {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, this))
        }
    }.getOrElse {
        Log.e("getBitmapOrNull", "$this")
        it.printStackTrace()
        null
    }
}


fun Uri.getAlbumArtBitmapOrElse(
    contentResolver: ContentResolver,
    size: Size,
    onFailure: (Throwable) -> Bitmap
): Bitmap {
    return kotlin.runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val base = MediaStore.Images.Media.getBitmap(contentResolver, this)
            createMatchScaledBitmap(base, size, true)
        } else {
            contentResolver.loadThumbnail(this, size, null)
        }
    }.getOrElse(onFailure)
}

/** `value` を [[ min, max ]] に収める */
fun <T : Comparable<T>> clamp(value: T, min: T, max: T) = if (value < min) min else if (value > max) max else value


fun getResourceUri(res: Resources, resId: Int): Uri {
    return Uri.parse(
        ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + res.getResourcePackageName(resId)
                + '/' + res.getResourceTypeName(resId)
                + '/' + res.getResourceEntryName(resId)
    )
}

fun createBitmapFromVectorDrawable(vecDrawable: VectorDrawable, size: Size): Bitmap {
    return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        vecDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vecDrawable.draw(canvas)
    }
}

fun createMatchScaledBitmap(src: Bitmap, maxSize: Size, filter: Boolean): Bitmap {
    val resizeScale = calcMatchScale(Size(src.width, src.height), maxSize)
    return Bitmap.createScaledBitmap(
        src, (src.width * resizeScale).roundToInt(), (src.height * resizeScale).roundToInt(), filter
    )
}

fun createMatchScaledBitmapFromDrawable(drawable: Drawable, maxSize: Size, filter: Boolean = true): Bitmap {
    return if (drawable is VectorDrawable) {  // さよなら可読性
        createBitmapFromVectorDrawable(
            drawable, (if (drawable.intrinsicWidth < 0 || drawable.intrinsicHeight < 0) 1.0 else calcMatchScale(
                Size(drawable.intrinsicWidth, drawable.intrinsicHeight), maxSize
            )).let { Size((drawable.intrinsicWidth * it).roundToInt(), (drawable.intrinsicHeight * it).roundToInt()) })
    } else {
        createMatchScaledBitmap((drawable as BitmapDrawable).bitmap, maxSize, filter)
    }
}


fun getAlbumArtUri(albumId: Long): Uri {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    } else {
        ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
    }
}

@Deprecated("", ReplaceWith(""))
fun getAlbumArt(
    ctx: Context,
    albumId: Long,
    size: Size,
    alt: Bitmap,
): Bitmap {
    val artUri = getAlbumArtUri(albumId)
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        try {
            @Suppress("DEPRECATION")
            val bmp = MediaStore.Images.Media.getBitmap(ctx.contentResolver, artUri)
            createMatchScaledBitmap(bmp, size, true)
        } catch (e: Exception) {
            alt
        }
    } else {
        try {
            ctx.contentResolver.loadThumbnail(artUri, size, null)
        } catch (e: Exception) {
//            Log.d("サムネを読み込めなかった", "理由: ${e}, Uri: $artUri")
            alt
        }
    }
}

@Deprecated("", ReplaceWith(""))
fun getAlbumArts(
    ctx: Context,
    albumId: Long,
    sizes: Pair<Size, Size>,
    alts: Pair<Bitmap, Bitmap>
): Pair<Bitmap, Bitmap> {
    val artUri = getAlbumArtUri(albumId)
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        try {
            @Suppress("DEPRECATION")
            val bmp = MediaStore.Images.Media.getBitmap(ctx.contentResolver, artUri)
            Pair(
                createMatchScaledBitmap(bmp, sizes.first, true),
                createMatchScaledBitmap(bmp, sizes.second, false)
            )
        } catch (e: Exception) {
            alts
        }
    } else {
        try {
            Pair(
                ctx.contentResolver.loadThumbnail(artUri, sizes.first, null),
                ctx.contentResolver.loadThumbnail(artUri, sizes.second, null)
            )
        } catch (e: Exception) {
            Log.d("サムネを読み込めなかった", "理由: ${e}, Uri: $artUri")
            alts
        }
    }
}

private fun calcMatchScale(srcSize: Size, maxSize: Size): Double {
    return if (srcSize.height <= srcSize.width) {
        maxSize.width.toDouble() / srcSize.width
    } else {
        maxSize.height.toDouble() / srcSize.height
    }
}