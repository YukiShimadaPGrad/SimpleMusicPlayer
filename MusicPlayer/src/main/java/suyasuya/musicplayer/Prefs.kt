package suyasuya.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDescription
import android.media.session.MediaSession.QueueItem
import android.net.Uri

class Prefs {
    companion object {
        private const val FILENAME_PLAYLISTS = "Prefs.Playlists"
        private const val KEY_PLAYLISTS = "Playlists"

        private const val FILENAME_AUDIO_FX = "Prefs.AudioFx"
        private const val KEY_EQUALIZER_ENABLED = "Equalizer.Enabled"
        private const val KEY_EQUALIZER_BAND_LEVELS = "Equalizer.BandLevels"
        private const val KEY_BASS_BOOST_STRENGTH = "BassBoost.Strength"

        private const val FILENAME_MUSIC_STATE = "Prefs.MusicState"
        private const val KEY_QUEUE = "Queue"
        private const val KEY_QUEUE_INDEX = "QueueIndex"
        private const val KEY_RANDOM_INDEX = "RandomIndex"
        private const val KEY_RANDOM_MODE = "RandomMode"
        private const val KEY_REPEAT_MODE = "RepeatMode"

        private const val DELIM = '@'

        data class EqualizerParams(val enabled: Boolean, val bandLevels: List<Short>)
        data class MusicState(
            val queue: List<QueueItem>,
            val index: Long,
            val shuffledIndex: List<Int>,
            val shuffleMode: Boolean,
            val repeatMode: MusicService.RepeatMode
        )

        /** 保存済みのプレイリストタイトル一覧を取得する */
        fun getPlaylistTitles(ctx: Context): List<String> {
            return ctx.getSharedPreferences(FILENAME_PLAYLISTS, Context.MODE_PRIVATE)
                .getStringSet(KEY_PLAYLISTS, HashSet())!!
                .toList()
                .sorted()
        }

        /** 指定したタイトルのプレイリストを取得する */
        fun getPlaylist(ctx: Context, title: String): List<Uri> {
            return ctx.getSharedPreferences(getPlaylistKey(title), Context.MODE_PRIVATE)
                .getStringSet(title, null)
                ?.toSortedList()
                ?.map { Uri.parse(it) }
                ?: ArrayList()
        }

        /** プレイリストを追加する 既存のタイトルと被っている場合は上書きする */
        fun addPlaylist(ctx: Context, title: String, uris: List<Uri>) {
            val titles = getPlaylistTitles(ctx).toMutableSet()
            titles += title
            savePlaylistTitles(ctx.getSharedPreferences(FILENAME_PLAYLISTS, Context.MODE_PRIVATE), titles)
            savePlaylist(ctx.getSharedPreferences(getPlaylistKey(title), Context.MODE_PRIVATE), title, uris)
        }

        /** 指定したプレイリストを削除する */
        fun deletePlaylist(ctx: Context, title: String) {
            val titles = getPlaylistTitles(ctx).toMutableSet()
            titles -= title
            savePlaylistTitles(ctx.getSharedPreferences(FILENAME_PLAYLISTS, Context.MODE_PRIVATE), titles)
            ctx.deleteSharedPreferences(getPlaylistKey(title))
        }

        /** イコライザの設定を取得する */
        fun getEqualizer(ctx: Context) =
            ctx.getSharedPreferences(FILENAME_AUDIO_FX, Context.MODE_PRIVATE).let {
                val enabled = it.getBoolean(KEY_EQUALIZER_ENABLED, false)
                val bandLevels = it.getStringSet(KEY_EQUALIZER_BAND_LEVELS, null)
                if (bandLevels == null) null
                else EqualizerParams(enabled, bandLevels.toSortedList().map { st -> st.toShort() })
            }

        /** イコライザの設定を保存する */
        fun saveEqualizer(ctx: Context, equalizer: EqualizerParams) =
            ctx.getSharedPreferences(FILENAME_AUDIO_FX, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_EQUALIZER_ENABLED, equalizer.enabled)
                .putStringSet(KEY_EQUALIZER_BAND_LEVELS, equalizer.bandLevels.toSortableSet())
                .apply()


        /** バスブーストの強さを取得する */
        fun getBassBoost(ctx: Context) =
            ctx.getSharedPreferences(FILENAME_AUDIO_FX, Context.MODE_PRIVATE).let {
                val strength = it.getInt(KEY_BASS_BOOST_STRENGTH, -1)
                if (strength < 0) null
                else strength.toShort()
            }

        /** バスブーストの強さを保存する */
        fun saveBassBoost(ctx: Context, strength: Short) {
            ctx.getSharedPreferences(FILENAME_AUDIO_FX, Context.MODE_PRIVATE).edit()
                .putInt(KEY_BASS_BOOST_STRENGTH, strength.toInt())
                .apply()
        }

        /** 再生キューやインデックスなどを保存する */
        fun saveMusicState(ctx: Context, dat: MusicState) =
            ctx.getSharedPreferences(FILENAME_MUSIC_STATE, Context.MODE_PRIVATE).edit()
                .putStringSet("${KEY_QUEUE}.ID", dat.queue.map { it.queueId }.toSortableSet())
                .putStringSet("${KEY_QUEUE}.MediaID", dat.queue.map { it.description.mediaId }.toSortableSet())
                .putStringSet("${KEY_QUEUE}.Title", dat.queue.map { it.description.title }.toSortableSet())
                .putStringSet("${KEY_QUEUE}.Subtitle", dat.queue.map { it.description.subtitle }.toSortableSet())
                .putStringSet("${KEY_QUEUE}.Description", dat.queue.map { it.description.description }.toSortableSet())
                .putStringSet("${KEY_QUEUE}.IconURI", dat.queue.map { it.description.iconUri }.toSortableSet())
                .putStringSet("${KEY_QUEUE}.MediaURI", dat.queue.map { it.description.mediaUri }.toSortableSet())
                .putLong(KEY_QUEUE_INDEX, dat.index)
                .putStringSet(KEY_RANDOM_INDEX, dat.shuffledIndex.toSortableSet())
                .putBoolean(KEY_RANDOM_MODE, dat.shuffleMode)
                .putString(KEY_REPEAT_MODE, dat.repeatMode.name)
                .apply()

        /** 再生キューやインデックスなどを取得する */
        fun getMusicState(ctx: Context) =
            ctx.getSharedPreferences(FILENAME_MUSIC_STATE, Context.MODE_PRIVATE).let { pf ->
                val ids = pf.getStringSet("${KEY_QUEUE}.ID", null)?.toSortedList()?.map { it.toLong() }
                val mediaIds = pf.getStringSet("${KEY_QUEUE}.MediaID", null)?.toSortedList()
                val titles = pf.getStringSet("${KEY_QUEUE}.Title", null)?.toSortedList()
                val subTitles = pf.getStringSet("${KEY_QUEUE}.Subtitle", null)?.toSortedList()
                val descriptions = pf.getStringSet("${KEY_QUEUE}.Description", null)?.toSortedList()
                val iconUris = pf.getStringSet("${KEY_QUEUE}.IconURI", null)?.toSortedList()?.map { Uri.parse(it) }
                val mediaUris = pf.getStringSet("${KEY_QUEUE}.MediaURI", null)?.toSortedList()?.map { Uri.parse(it) }
                val queueItems = (0 until (ids?.size ?: 0)).map { i ->
                    QueueItem(
                        MediaDescription.Builder()
                            .setMediaId(mediaIds!![i])
                            .setTitle(titles!![i])
                            .setSubtitle(subTitles!![i])
                            .setDescription(descriptions!![i])
                            .setIconUri(iconUris!![i])
                            .setMediaUri(mediaUris!![i])
                            .build(), ids!![i]
                    )
                }
                MusicState(
                    queueItems,
                    pf.getLong(KEY_QUEUE_INDEX, 0),
                    pf.getStringSet(KEY_RANDOM_INDEX, null)?.toSortedList()?.map { it.toInt() } ?: ArrayList(),
                    pf.getBoolean(KEY_RANDOM_MODE, false),
                    MusicService.RepeatMode.valueOf(
                        pf.getString(KEY_REPEAT_MODE, MusicService.RepeatMode.RepeatQueue.name)!!
                    )
                )
            }


        private fun <T> List<T>.toSortableSet() = map { it?.toString() ?: "" }
            .mapIndexed { i, st -> "$i$DELIM$st" }
            .toSet()

        private fun Set<String>.toSortedList() = toList()
            .sortedBy { it.take(it.indexOf(DELIM)).toInt() }
            .map { it.drop(it.indexOf(DELIM) + 1) }


        private fun getPlaylistKey(title: String) = "Prefs.playlist_$title"
        private fun savePlaylistTitles(prefs: SharedPreferences, titles: Set<String>) {
            prefs.edit().putStringSet(KEY_PLAYLISTS, titles).apply()
        }

        private fun savePlaylist(prefs: SharedPreferences, title: String, uris: List<Uri>) {
            prefs.edit()
                .putStringSet(title, uris.toSortableSet())
                .apply()
        }
    }
}


