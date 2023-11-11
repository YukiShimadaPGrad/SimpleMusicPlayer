package suyasuya.musicplayer

import android.content.ContentUris
import android.content.Context
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser.MediaItem
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.Audio
import android.util.Log


class MusicLibrary {

    enum class SortKey {
        TITLE, SubTitle, Album, Duration;
    }

    private inline fun <R : Comparable<R>> MutableList<MediaItem>.sortByKeyI(
        flg: Boolean, crossinline fnc: (MediaItem) -> R?
    ): MutableList<MediaItem> = this.apply { if (flg) sortBy(fnc) else sortByDescending(fnc) }

    private fun MutableList<MediaItem>.sortByKey(key: SortKey, isAscending: Boolean): MutableList<MediaItem> {
        return when (key) {
            SortKey.TITLE -> sortByKeyI(isAscending) { it.description.title.toString() }
            SortKey.SubTitle -> sortByKeyI(isAscending) { it.description.subtitle.toString() }
            SortKey.Album -> sortByKeyI(isAscending) { it.description.extras?.getString(MediaMetadata.METADATA_KEY_ALBUM) }
            SortKey.Duration -> sortByKeyI(isAscending) { it.description.extras?.getLong(MediaMetadata.METADATA_KEY_DURATION) }
        }
    }


    companion object {
        private val TAG = this::class.qualifiedNameWithoutCompanion
        private const val MEDIA_ID_ROOT = "root"
        private const val MEDIA_ID_KIND_ALBUM = "Album"
        private const val MEDIA_ID_KIND_PLAYLIST = "Playlist"
        private const val MEDIA_ID_KIND_ALL = "AllMusic"
        private const val ID_DELIM = '@'
        private const val SUBTITLE_DEFAULT = "<unknown>"
    }

    private val albumMap = HashMap<Long, MediaMetadata>()
    private val audioMap = HashMap<Long, MediaMetadata>()
    private val mediaIdKindAvailableList =
        arrayOf(
            Pair(MEDIA_ID_KIND_ALBUM, R.drawable.album),
            Pair(MEDIA_ID_KIND_PLAYLIST, R.drawable.playlist),
            Pair(MEDIA_ID_KIND_ALL, R.drawable.audio_file)
        )
    private val topMediaItems = ArrayList<MediaItem>(mediaIdKindAvailableList.size)
    private val playlistMap = HashMap<String, MediaItem>()

    private var _initialized = false
    val initialized: Boolean
        get() = _initialized


    fun getRootId(): String = MEDIA_ID_ROOT

    fun getMediaItems(ctx: Context, parentMediaId: String): Pair<Boolean, MutableList<MediaItem>?> {
        if (parentMediaId == MEDIA_ID_ROOT) {
            Log.d(TAG, "getMediaItems() root")
            return Pair(false, topMediaItems)
        }
        if (!_initialized) {
            throw IllegalStateException("${this::prepare::class.qualifiedName} を呼び忘れているぞ!")
        }
        return if (parentMediaId == MEDIA_ID_KIND_ALL) {
            Log.d(TAG, "getMediaItems() 全曲選曲モード parentMediaId: $parentMediaId")
            Pair(true, getAll())
        } else if (parentMediaId == MEDIA_ID_KIND_ALBUM) {
            Log.d(TAG, "getMediaItems() アルバム選択モード parentMediaId: $parentMediaId")
            Pair(false, getAlbums())
        } else if (parentMediaId == MEDIA_ID_KIND_PLAYLIST) {
            Log.d(TAG, "getMediaItems() プレイリスト選択モード parentMediaId: $parentMediaId")
            Pair(false, getPlaylists(ctx))
        } else if (parentMediaId.startsWith("$MEDIA_ID_KIND_ALBUM$ID_DELIM")) {
            Log.d(TAG, "getMediaItems() アルバム内選曲モード parentMediaId: $parentMediaId")
            Pair(true, getAlbumMusics(parentMediaId.removePrefix("$MEDIA_ID_KIND_ALBUM$ID_DELIM").toLong()))
        } else if (parentMediaId.startsWith("$MEDIA_ID_KIND_PLAYLIST$ID_DELIM")) {
            Log.d(TAG, "getMediaItems() プレイリスト内選曲モード parentMediaId: $parentMediaId")
            Pair(true, getPlaylistMusics(ctx, parentMediaId.removePrefix("$MEDIA_ID_KIND_PLAYLIST$ID_DELIM")))
        } else {
            Log.e(TAG, "getMediaItems 想定外のparentMediaId: $parentMediaId")
            Pair(false, null)
        }
    }

    fun getMetadata(desc: MediaDescription): MediaMetadata {
        val id = desc.mediaId ?: throw IllegalArgumentException("desc.mediaId はnullじゃだめ！")
        try {
            return audioMap.getValue(id.toLong())
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("desc.mediaIdは数字に変換できる文字列じゃないといけない: $id")
        } catch (e: NoSuchElementException) {
            if (!_initialized) {
                throw IllegalStateException("\"${this::prepare.name}() を呼び忘れてるぞ!")
            } else {
                throw IllegalArgumentException("知らないMediaDescriptionが渡された", e)
            }
        }
    }

    private fun getAll(): MutableList<MediaItem> = getMusicsBase(SortKey.TITLE, true) { true }

    private fun getAlbums(): MutableList<MediaItem> = albumMap.map { it.value }
        .sortedBy { it.getString(MediaMetadata.METADATA_KEY_ALBUM) }
        .map { MediaItem(it.description, MediaItem.FLAG_BROWSABLE) }
        .toMutableList()

    private fun getPlaylists(ctx: Context): MutableList<MediaItem> = Prefs.getPlaylistTitles(ctx).map {
        MediaItem(
            MediaDescription.Builder().setTitle(it).setMediaId("$MEDIA_ID_KIND_PLAYLIST$ID_DELIM$it").build(),
            MediaItem.FLAG_BROWSABLE
        )
    }.toMutableList()
        .apply { sortedBy { it.description.toString() } }


    private fun getAlbumMusics(albumId: Long): MutableList<MediaItem> {
        val album = albumMap[albumId]?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        return getMusicsBase(SortKey.TITLE, true) {
            it.value.getString(MediaMetadata.METADATA_KEY_ALBUM) == album
        }
    }

    private fun getPlaylistMusics(ctx: Context, playlistTitle: String): MutableList<MediaItem> {
        val playlist: List<Uri>
        try {
            playlist = Prefs.getPlaylist(ctx, playlistTitle)
        } catch (e: Exception) {
            e.printStackTrace()
            return ArrayList()
        }
        return playlist.mapNotNull { uri ->
            audioMap.values.find { it.description.mediaUri == uri }
        }.metadata2Description()
            .map { MediaItem(it, MediaItem.FLAG_PLAYABLE) }
            .toMutableList()
    }

    @Deprecated("よく考えたらこれを使うべき状態がない")
    private fun getMusic(mediaId: Long): MutableList<MediaItem> = try {
        mutableListOf(MediaItem(audioMap.getValue(mediaId).description, MediaItem.FLAG_PLAYABLE))
    } catch (e: NoSuchElementException) {
        if (!_initialized) {
            throw IllegalStateException("\"${this::prepare::class.qualifiedName} を呼び忘れてるぞ!")
        } else {
            throw IllegalArgumentException("知らないMediaDescriptionが渡された", e)
        }
    }

    private fun List<MediaMetadata>.metadata2Description(): List<MediaDescription> = this.map {
        MediaDescription.Builder()
            .setMediaId(it.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
            .setMediaUri(Uri.parse(it.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)))
            .setDescription(it.getString(MediaMetadata.METADATA_KEY_ALBUM))
//                .setIconBitmap(it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))
            .setTitle(it.getString(MediaMetadata.METADATA_KEY_TITLE))
            .setSubtitle(it.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: SUBTITLE_DEFAULT)
            .setIconUri(Uri.parse(it.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)))
            .setExtras(Bundle().apply {
                putLong(MediaMetadata.METADATA_KEY_DURATION, it.getLong(MediaMetadata.METADATA_KEY_DURATION))
                putString(MediaMetadata.METADATA_KEY_ALBUM, it.getString(MediaMetadata.METADATA_KEY_ALBUM))
            })
            .build()
    }

    // 実際はfilterの後にsortだが、kotlin的に引数の順序はこれが便利
    @Suppress("SameParameterValue")
    private fun getMusicsBase(
        sortKey: SortKey,
        isAscending: Boolean,
        filterFun: (Map.Entry<Long, MediaMetadata>) -> Boolean,
    ): MutableList<MediaItem> = audioMap.filter(filterFun)
        .values.toList()
        .metadata2Description()
        .map { MediaItem(it, MediaItem.FLAG_PLAYABLE) }
        .toMutableList()
        .sortByKey(sortKey, isAscending)


    /** 端末内の音声ファイルの読み込みを行い
     * @param force true なら、すでに読み込み済みでももう一度全て読み直す
     */
    fun prepare(ctx: Context, force: Boolean) {
        if (_initialized && !force) {
            return
        }
        topMediaItems.clear()
        playlistMap.clear()
        albumMap.clear()
        audioMap.clear()

        topMediaItems.addAll(mediaIdKindAvailableList.map { (st, rid) ->
            MediaItem(
                MediaDescription.Builder().setMediaId(st).setDescription(st).setTitle(st)
                    .setIconUri(getResourceUri(ctx.resources, rid))
                    .build(),
                MediaItem.FLAG_BROWSABLE
            )
        })

        val cursor = ctx.contentResolver.query(
            Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                Audio.Media._ID,
                Audio.Albums.ALBUM_ID,
                Audio.Media.ALBUM,
                Audio.Media.ARTIST,
                Audio.Media.DURATION,
                Audio.Media.TITLE,
            ),
            null,
            null,
            Audio.Media.ALBUM
        )!!
        if (!cursor.moveToFirst()) {
            Log.w(TAG, "音楽がひとつも見つからなかった")
            return
        }
        do {
            val musicId = cursor.getLong(0)
            val uri = ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, musicId)
            val albumId = cursor.getLong(1)
            if (!albumMap.contains(albumId)) {
                albumMap[albumId] = MediaMetadata.Builder().run {
                    putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "$MEDIA_ID_KIND_ALBUM$ID_DELIM$albumId")
                    putString(MediaMetadata.METADATA_KEY_ALBUM, cursor.getString(2))
                    getAlbumArtUri(albumId).toString().also {
                        putString(MediaMetadata.METADATA_KEY_ART_URI, it)
                        putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, it)
                    }
                    build()
                }
            }
            audioMap[musicId] = MediaMetadata.Builder().run {
                putString(MediaMetadata.METADATA_KEY_MEDIA_ID, cursor.getLong(0).toString())
                putString(MediaMetadata.METADATA_KEY_MEDIA_URI, uri.toString())
                putString(MediaMetadata.METADATA_KEY_ALBUM, cursor.getString(2))
                putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, cursor.getString(2))
                putString(MediaMetadata.METADATA_KEY_ARTIST, cursor.getString(3))
                putLong(MediaMetadata.METADATA_KEY_DURATION, cursor.getLong(4))
                putString(MediaMetadata.METADATA_KEY_TITLE, cursor.getString(5))
                putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, getAlbumArtUri(albumId).toString())
                build()
            }

        } while (cursor.moveToNext())
        cursor.close()
        _initialized = true
    }
}