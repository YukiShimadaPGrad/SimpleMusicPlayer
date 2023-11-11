package suyasuya.musicplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.*
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.browse.MediaBrowser.MediaItem
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSession.QueueItem
import android.media.session.PlaybackState
import android.os.*
import android.service.media.MediaBrowserService
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import java.util.*
import kotlin.math.roundToInt


class MusicService : MediaBrowserService() {

    enum class RepeatMode {
        NoRepeat, RepeatQueue, RepeatOneMusic
    }

    companion object {
        private val TAG = this::class.qualifiedNameWithoutCompanion!!
        private val NOTIFICATION_CHANNEL_ID = TAG

        //        private const val INTENT_ACTION_MEDIA_BUTTON = Intent.ACTION_MEDIA_BUTTON
        val INTENT_ACTION_MEDIA_BUTTON = "${TAG}_FROM_NOTIFICATION"
        val INTENT_ACTION_CHANGE_REPEAT_MODE = "${TAG}_CHANGE_REPEAT_MODE"
        val INTENT_EXTRA_REPEAT_MODE = "${TAG}_EXTRA_REPEAT_MODE"

        val BUNDLE_KEY_PLAYBACK_STATE_REPEAT = "${TAG}_bundle_key_playback_state_repeat"
        val BUNDLE_KEY_PLAYBACK_STATE_SHUFFLE = "${TAG}_bundle_key_playback_state_shuffle"
        val BUNDLE_KEY_QUEUE = "${TAG}_key_queue"
        val BUNDLE_KEY_MEDIA_ITEM = "${TAG}_key_media_item"
        val CUSTOM_ACTION_TOGGLE_REPEAT_MODE = "${TAG}_custom_action_toggle_repeat_mode"
        val CUSTOM_ACTION_TOGGLE_SHUFFLE_MODE = "${TAG}_custom_action_toggle_shuffle_mode"
        val CUSTOM_ACTION_RENEW_QUEUE = "${TAG}_custom_action_renew_queue"
        val CUSTOM_ACTION_ADD_QUEUE = "${TAG}_custom_action_add_queue"
        val CUSTOM_ACTION_RELOAD_MUSIC_LIBRARY = "${TAG}_custom_action_music_library"
        val CUSTOM_ACTION_NOTIFY_AUDIO_FX_CHANGED = "${TAG}_custom_action_notify_audio_fx_changed"

        val COMMAND_GET_AUDIO_SESSION_ID = "${TAG}_command_get_audio_session_id"
//        val COMMAND_CB_PARAM_GET_AUDIO_SESSION_ID = "${TAG}_command_cb_param_get_audio_session_id"

        const val INTENT_ID = 8

        private const val MEDIA_PLAYER_VOLUME_NORMAL = 1.0f
        private const val MEDIA_PLAYER_VOLUME_DUCKING = 0.2f
        private const val NOTIFICATION_ID = 1
        private const val UPDATE_PLAYBACK_STATE_DURATION_MS = 500L
        private const val BUNDLE_KEY_DONT_REFRESH_QUEUE = "bundle_dont_key_refresh_queue"
    }

    private lateinit var iconNotification: Icon
    private lateinit var iconPrevious: Icon
    private lateinit var iconPause: Icon
    private lateinit var iconPlay: Icon
    private lateinit var iconNext: Icon
    private lateinit var iconRepeat: Icon
    private lateinit var iconRepeatOn: Icon
    private lateinit var iconRepeatOneOn: Icon

    private lateinit var notificationLargeIconSize: Size
    private lateinit var bitmapDefaultAlbumArt: Bitmap
    private lateinit var bitmapCurrentAlbumArt: Bitmap

    private lateinit var am: AudioManager //AudioFocusを扱うためのManager

    private lateinit var mSession: MediaSession
    private var repeatMode = RepeatMode.RepeatQueue
    private var shuffleMode = false

    private lateinit var mediaPlayer: MediaPlayer //音楽プレイヤーの実体
    private lateinit var equalizer: Equalizer
    private lateinit var bassBoost: BassBoost
    private var musicLibrary = MusicLibrary()

    private var nextQueueItems = ArrayList<QueueItem>()
    private var shuffledIndexes = ArrayList<Int>()
    private val focusLock = Any()
    private var playbackDelayed = false
    private var playbackNowAuthorized = false
    private var resumeOnFocusGain = false
    private lateinit var myLooperHandler: Handler


    //オーディオフォーカスのコールバック
    private val afChangeListener = OnAudioFocusChangeListener { focusChange ->
        //フォーカスを完全に失ったら
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                mSession.controller.transportControls.pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { //一時的なフォーカスロスト
                synchronized(focusLock) {
                    resumeOnFocusGain = true
                    playbackDelayed = false
                }
                mSession.controller.transportControls.pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                //通知音とかによる一時的なフォーカスロスト ボリュームを下げる or 止める
                mediaPlayer.setVolume(MEDIA_PLAYER_VOLUME_DUCKING, MEDIA_PLAYER_VOLUME_DUCKING)
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (playbackDelayed || resumeOnFocusGain) {
                    synchronized(focusLock) {
                        playbackDelayed = false
                        resumeOnFocusGain = false
                    }
                    mediaPlayer.setVolume(MEDIA_PLAYER_VOLUME_NORMAL, MEDIA_PLAYER_VOLUME_NORMAL)
                    mSession.controller.transportControls.play()
                }
            }
        }
    }

    private val afRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
        setAudioAttributes(AudioAttributes.Builder().run {
            setUsage(AudioAttributes.USAGE_MEDIA)
            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            build()
        })
        setAcceptsDelayedFocusGain(true)
        setOnAudioFocusChangeListener(afChangeListener)
        build()
    }

    // private fn updatePlaybackState() を定期的に呼び出す為の関数
    private val updatePlaybackStateLoop = object : Runnable {  // thisを使いたいからSAM変換は使わない
        override fun run() {
            Log.d(TAG, "再生情報更新ループ(Handler.postDelayed)")
            //再生中は常に、再生中断後に1度アップデート
            val state = mSession.controller.playbackState
            if (mediaPlayer.isPlaying
                || (!mediaPlayer.isPlaying && state != null && state.state == PlaybackState.STATE_PLAYING)
            ) {
                updatePlaybackState()
                myLooperHandler.postDelayed(this, UPDATE_PLAYBACK_STATE_DURATION_MS)  //再度実行
            }
        }
    }

    //MediaSession用コールバック
    private val mediaSessionCallback: MediaSession.Callback = object : MediaSession.Callback() {

        //曲のIDから再生する
        //WearやAutoのブラウジング画面から曲が選択された場合もここが呼ばれる
        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId")
            if (extras == null
                || !extras.getBoolean(BUNDLE_KEY_DONT_REFRESH_QUEUE, false)
                || mSession.queue.isNullOrEmpty()
            ) {
                if (nextQueueItems.isEmpty()) {
                    Log.e(TAG, "onPlayFromMediaId キューとして使えるものが何もない!")
                }
                mSession.setQueue(nextQueueItems)
            }
            var queueIndex = extras?.getInt("_index", -1) ?: -1
            if (queueIndex == -1 && mediaId == mSession.controller.metadata?.description?.mediaId) {
                queueIndex = mSession.controller.playbackState?.activeQueueItemId?.toInt() ?: -1
            }
            if (queueIndex == -1) {
                //今回は簡易的にmediaIdからインデックスを割り出す。
                for (item in mSession.queue!!) if (item.description.mediaId == mediaId) {
                    queueIndex = item.queueId.toInt()
                    break
                }
            }
            mediaPlayer.reset()
            mediaPlayer.setDataSource(applicationContext, mSession.queue!![queueIndex].description.mediaUri!!)
            mediaPlayer.prepare()  // ホントはprepareAsync()
            mSession.isActive = true
            onPlay()
            updatePlaybackState(queueIndex)
            //MediaSessionが配信する、再生中の曲の情報を設定
            bitmapCurrentAlbumArt =
                mSession.currentQueueItem!!.description?.iconUri?.getAlbumArtBitmapOrElse(
                    contentResolver,
                    notificationLargeIconSize
                ) { bitmapDefaultAlbumArt } ?: bitmapCurrentAlbumArt
            // putBitmap() はAndroid13以降の通知欄メディアコントローラに綺麗なサムネを表示するため
            mSession.setMetadata(
                MediaMetadata.Builder(musicLibrary.getMetadata(mSession.currentQueueItem!!.description))
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmapCurrentAlbumArt).build()
            )

        }

        //再生をリクエストされたとき
        override fun onPlay() {
            Log.d(TAG, "onPlay")
            val res = am.requestAudioFocus(afRequest)
            synchronized(focusLock) {
                playbackNowAuthorized = when (res) {
                    AudioManager.AUDIOFOCUS_REQUEST_FAILED -> false
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                        //取得できたら再生を始める
                        mSession.isActive = true
                        mediaPlayer.start()
                        myLooperHandler.removeCallbacks(updatePlaybackStateLoop)
                        myLooperHandler.postDelayed(updatePlaybackStateLoop, UPDATE_PLAYBACK_STATE_DURATION_MS)
                        true
                    }

                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                        playbackDelayed = true
                        false
                    }

                    else -> false
                }
            }
        }

        //一時停止をリクエストされたとき
        override fun onPause() {
            Log.d(TAG, "onPause")
            mediaPlayer.pause()
            //オーディオフォーカスを開放
            am.abandonAudioFocusRequest(afRequest)
        }

        //停止をリクエストされたとき
        override fun onStop() {
            Log.d(TAG, "onStop")
            mediaPlayer.stop()
            mSession.isActive = false
            //オーディオフォーカスを開放
            am.abandonAudioFocusRequest(afRequest)
        }

        //シークをリクエストされたとき
        override fun onSeekTo(pos: Long) {
            Log.d(TAG, "onSeekTo pos: $pos")
            mediaPlayer.seekTo(pos.toInt())
            updatePlaybackState()
        }

        //次の曲をリクエストされたとき
        override fun onSkipToNext() {
            Log.d(TAG, "onSkipToNext")
            onSkipToNeighbour(true)
        }

        //前の曲をリクエストされたとき
        override fun onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious")
            onSkipToNeighbour(false)
        }

        // onSkipToNext と onSkipToPrevious の共通処理
        private fun onSkipToNeighbour(isNext: Boolean) {
            val queueItems = mSession.queue
            if (queueItems.isNullOrEmpty()) {
                Log.w(TAG, "キューが空っぽだぞ")
                return
            }
            /*if (repeatMode == RepeatMode.RepeatOneMusic) {
                mSession.queueIndex!!.toLong()
            } else */
            val index = if (shuffleMode) {
                Collections.rotate(shuffledIndexes, if (isNext) -1 else 1)
                shuffledIndexes.last().toLong()
            } else (queueItems.size + mSession.queueIndex!! + if (isNext) 1 else -1) % queueItems.size
            onSkipToQueueItem(index)
        }

        //WearやAutoでキュー内のアイテムを選択された際にも呼び出される
        override fun onSkipToQueueItem(i: Long) {
            Log.d(TAG, "onSkipToQueueItem i: $i")
            val queueItems = mSession.queue
            if (queueItems.isNullOrEmpty()) {
                Log.w(TAG, "キューが空っぽだぞ")
                return
            }
            val queueIndex = ((queueItems.size + i) % queueItems.size).toInt()
            onPlayFromMediaId(queueItems[queueIndex].description.mediaId!!,
                Bundle().apply {
                    putBoolean(BUNDLE_KEY_DONT_REFRESH_QUEUE, true)
                    putInt("_index", queueIndex)
                })
        }

        override fun onRewind() {
            Log.d(TAG, "onRewind")
            onSeekTo(0)
            onPlay()
        }

        //Media Button Intentが飛んできた時に呼び出される
        //オーバーライド不要（今回はログを吐くだけ）
        //MediaSessionのplaybackStateのActionフラグに応じてできる操作が変わる
        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val key = mediaButtonEvent.getParcelableExtraCMP(Intent.EXTRA_KEY_EVENT, KeyEvent::class)
            Log.d(TAG, "onMediaButtonEvent keycode: ${key!!.keyCode}")
            return super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onCommand(command: String, args: Bundle?, cb: ResultReceiver?) {
            when (command) {
                COMMAND_GET_AUDIO_SESSION_ID -> cb?.send(mediaPlayer.audioSessionId, null)
                    ?: Log.w(TAG, "onCommand \"$COMMAND_GET_AUDIO_SESSION_ID\"にコールバックが渡されなかったぞ")

                else -> Log.w(TAG, "on")
            }
        }


        // このアプリ独自の操作
        override fun onCustomAction(action: String, extras: Bundle?) {
            Log.d(TAG, "onCustomAction action: $action, extras: $extras")
            when (action) {
                CUSTOM_ACTION_TOGGLE_REPEAT_MODE -> {
                    var next = repeatMode.ordinal + 1
                    if (RepeatMode.values().size <= next) next = 0
                    repeatMode = RepeatMode.values()[next]
                    updatePlaybackState()
                }

                CUSTOM_ACTION_TOGGLE_SHUFFLE_MODE -> {
                    shuffleMode = !shuffleMode
                    if (shuffleMode && !mSession.queue.isNullOrEmpty()) {
                        shuffledIndexes.clear()
                        shuffledIndexes.addAll((0 until mSession.queue!!.size).shuffled())
                    }
                    updatePlaybackState()
                }

                CUSTOM_ACTION_RENEW_QUEUE -> {
                    val newMediaItems = extras?.getParcelableArrayListCMP(BUNDLE_KEY_QUEUE, MediaItem::class)
                    if (newMediaItems == null) {
                        Log.e(TAG, "カスタムアクション: $CUSTOM_ACTION_RENEW_QUEUE には、ArrayList<MediaItem>を持ったextrasが必要")
                        return
                    }
                    val crrQueueItem = mSession.currentQueueItem
                    renewQueue(newMediaItems, true)
                    val queueItems = mSession.queue!!
                    var queueIndex = mSession.queueIndex!!.toInt()
                    if (crrQueueItem != null) {
                        if (queueItems.size <= queueIndex || queueItems[queueIndex] != crrQueueItem) {
                            if (queueItems.getOrNull(queueIndex - 1)?.description == crrQueueItem.description) {
                                --queueIndex  // 再生中ではない曲を動かした結果再生中の曲が上にずれた場合
                            } else if (queueItems.getOrNull(queueIndex + 1)?.description == crrQueueItem.description) {
                                ++queueIndex  // 再生中ではない曲を動かした結果再生中の曲が下にずれた場合
                            } else {  // 再生中の曲を動かした場合: 再生中の曲と同じ曲が複数ある場合正確な位置の特定は無理なので上から探索
                                for ((i, v) in queueItems.withIndex()) {
                                    if (v.description.mediaId == crrQueueItem.description.mediaId) {
                                        queueIndex = i
                                        break
                                    }
                                }
                            }
                            updatePlaybackState(queueIndex)
                        }
                    }
                }

                CUSTOM_ACTION_ADD_QUEUE -> {
                    val newMediaItem = extras?.getParcelableCMP(BUNDLE_KEY_MEDIA_ITEM, MediaItem::class)
                    if (newMediaItem == null) {
                        Log.e(TAG, "カスタムアクション: $CUSTOM_ACTION_ADD_QUEUE には、MediaItemを持ったextrasが必要")
                        return
                    }
                    val queueItems = mSession.queue ?: ArrayList()
                    if (newMediaItem.isPlayable) {  // 1曲追加
                        queueItems += QueueItem(newMediaItem.description, queueItems.size.toLong())
                        mSession.setQueue(queueItems)
                        return
                    }
                    val (queueable, mediaItems)
                            = newMediaItem.mediaId?.let { musicLibrary.getMediaItems(applicationContext, it) }
                        ?: Pair(null, null)
                    if (mediaItems == null || queueable == null || !queueable) {
                        Log.w(
                            TAG, "カスタムアクション: $CUSTOM_ACTION_ADD_QUEUE " +
                                    "に渡されたMediaItemあるいは直下の物がPlayableではなかったので何も追加されなかった!"
                        )
                        return
                    }
                    queueItems.addAll(mediaItems.filter { it.isPlayable }
                        .mapIndexed { i, v -> QueueItem(v.description, queueItems.size + i.toLong()) })
                    mSession.setQueue(queueItems)
                }

                CUSTOM_ACTION_RELOAD_MUSIC_LIBRARY -> musicLibrary.prepare(applicationContext, true)
                CUSTOM_ACTION_NOTIFY_AUDIO_FX_CHANGED -> updateAudioFxFromSharedPrefs()
                else -> super.onCustomAction(action, extras)
            }
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
//                Log.d(TAG, "onPlaybackStateChanged state: $state")
            if (state?.state == PlaybackState.STATE_STOPPED) {
                when (repeatMode) {
                    RepeatMode.NoRepeat -> {}
                    RepeatMode.RepeatQueue -> if (mediaPlayer.duration <= mediaPlayer.currentPosition) {
                        mSession.controller.transportControls.skipToNext()
                    }

                    RepeatMode.RepeatOneMusic -> if (mediaPlayer.duration <= mediaPlayer.currentPosition) {
                        mSession.controller.transportControls.rewind()
                    }
                }
            }
            createNotification()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(TAG, "onMetadataChanged metadata: ${metadata?.description}")
            createNotification()
            saveState()
        }

        override fun onQueueChanged(queue: MutableList<QueueItem>?) {
            Log.d(TAG, "onQueueChanged")
            shuffledIndexes.clear()
            if (queue.isNullOrEmpty()) {
                Log.w(TAG, "queue が空っぽだぞ!")
            } else {
                mSession.queue?.size?.let {
                    shuffledIndexes.addAll((0 until it).shuffled())
                }
            }
            saveState()
        }

        private fun saveState() = Prefs.saveMusicState(
            applicationContext,
            Prefs.Companion.MusicState(
                mSession.queue ?: ArrayList(),
                mSession.queueIndex ?: 0,
                shuffledIndexes,
                shuffleMode,
                repeatMode
            )
        )

    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        iconNotification = Icon.createWithResource(applicationContext, R.drawable.ic_notification)
        iconPrevious = Icon.createWithResource(applicationContext, R.drawable.controls_previous)
        iconPause = Icon.createWithResource(applicationContext, R.drawable.controls_pause)
        iconPlay = Icon.createWithResource(applicationContext, R.drawable.controls_play)
        iconNext = Icon.createWithResource(applicationContext, R.drawable.controls_next)
        iconRepeat = Icon.createWithResource(applicationContext, R.drawable.controls_repeat)
        iconRepeatOn = Icon.createWithResource(applicationContext, R.drawable.controls_repeat_on)
        iconRepeatOneOn = Icon.createWithResource(applicationContext, R.drawable.controls_repeat_one_on)
        notificationLargeIconSize =
            applicationContext.resources.getDimension(R.dimen.icon_notification_large_size).roundToInt()
                .let { Size(it, it) }
        bitmapDefaultAlbumArt = createMatchScaledBitmapFromDrawable(
            applicationContext.getDrawable(R.drawable.album_art_default)!!,
            notificationLargeIconSize
        )

        //AudioManagerを取得
        am = getSystemService(AUDIO_SERVICE) as AudioManager
        //MediaSessionを初期化
        mSession = MediaSession(applicationContext, TAG)
        //このMediaSessionが提供する機能を設定
        // 設定しなくても良さげ？
//        mSession.setFlags(
//            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or  //ヘッドフォン等のボタンを扱う
////                    MediaSession.FLAG_HANDLES_QUEUE_COMMANDS or  //キュー系のコマンドの使用をサポート
//                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
//        ) //再生、停止、スキップ等のコントロールを提供

        //クライアントからの操作に応じるコールバックを設定
        mSession.setCallback(mediaSessionCallback)

        //MediaBrowserServiceにSessionTokenを設定
        sessionToken = mSession.sessionToken

        //Media Sessionのメタデータや、プレイヤーのステータスが更新されたタイミングで
        //通知の作成/更新をする
        mSession.controller.registerCallback(mediaControllerCallback)

        mediaPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            bassBoost = BassBoost(0, audioSessionId)
            equalizer = Equalizer(0, audioSessionId)
            updateAudioFxFromSharedPrefs()
        }

        myLooperHandler = Handler(mainLooper)

        Prefs.getMusicState(applicationContext).also {
            if (it.queue.isNotEmpty()) {
                musicLibrary.prepare(applicationContext, true)
                mSession.setQueue(it.queue)
                mediaSessionCallback.onSkipToQueueItem(it.index)
                mediaSessionCallback.onPause()
                shuffledIndexes = ArrayList(it.shuffledIndex)
            }
            shuffleMode = it.shuffleMode
            repeatMode = it.repeatMode
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (intent?.action == INTENT_ACTION_MEDIA_BUTTON && intent.hasExtra(Intent.EXTRA_KEY_EVENT)) {
            Log.d(TAG, "\taction: $INTENT_ACTION_MEDIA_BUTTON")
            mSession.controller.dispatchMediaButtonEvent(
                intent.getParcelableExtraCMP(Intent.EXTRA_KEY_EVENT, KeyEvent::class)!!
            )
        } else if (intent?.action == INTENT_ACTION_CHANGE_REPEAT_MODE && intent.hasExtra(INTENT_EXTRA_REPEAT_MODE)) {
            Log.d(TAG, "\t action: $INTENT_ACTION_CHANGE_REPEAT_MODE")
            repeatMode = intent.getSerializableExtraCMP(INTENT_EXTRA_REPEAT_MODE, RepeatMode::class)
                ?: RepeatMode.NoRepeat
            updatePlaybackState()
            Log.d(TAG, "\trepeatMode設定: $repeatMode")
        } else {
            Log.d(TAG, "\tactionのないintent: $intent は初回起動だからだよね?")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")  // この時点でmSessionがゴミになってるっぽい
        myLooperHandler.removeCallbacks(updatePlaybackStateLoop)
        bassBoost.release()
        equalizer.release()
        mediaPlayer.release()
        mSession.controller.unregisterCallback(mediaControllerCallback)
        mSession.release()
        super.onDestroy()
    }


    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        Log.d(TAG, "onGetRoot 接続元pkg:$clientPackageName uid:$clientUid")
        if (!musicLibrary.initialized) {
            musicLibrary.prepare(this, true)
        }
        return BrowserRoot(musicLibrary.getRootId(), null)
    }

    //クライアント側がsubscribeを呼び出すと呼び出される
    //音楽ライブラリの内容を返す
    //WearやAutoで表示される曲のリストにも使われる
    //デフォルトでonGetRootで返した文字列がparentMediaIdに渡される
    //ブラウザ画面で子要素を持っているMediaItemを選択した際にもそのIdが渡される
    override fun onLoadChildren(parentMediaId: String, result: Result<MutableList<MediaItem>>) {
        Log.d(TAG, "onLoadChildren parentMediaId: $parentMediaId")
        val (makeQueue, mediaItems) = musicLibrary.getMediaItems(applicationContext, parentMediaId)
        if (mediaItems == null) {
            result.sendResult(null)
            return
        }
        if (makeQueue) {
            renewQueue(mediaItems, false)

        }
        result.sendResult(mediaItems)
    }


    private fun renewQueue(mediaItems: List<MediaItem>, updateCurrent: Boolean) {
        if (updateCurrent) {
            mSession.queue ?: ArrayList()
        } else {
            nextQueueItems
        }.also { queue ->
            queue.clear()
            mediaItems.withIndex()
                .forEach { queue += QueueItem(it.value.description, it.index.toLong()) }
            if (updateCurrent) {
                mSession.setQueue(queue)
                updatePlaybackState()
            }
        }
    }


    //MediaSessionが配信する、現在のプレイヤーの状態を設定する
    //ここには再生位置の情報も含まれるので定期的に更新する
    private fun updatePlaybackState(index: Int? = null) {
        val queueIndex = index ?: mSession.queueIndex ?: 0
        val state = if (mediaPlayer.isPlaying) {
            PlaybackState.STATE_PLAYING
        } else {
            if (mediaPlayer.currentPosition <= 0 || mediaPlayer.duration <= mediaPlayer.currentPosition) {
                PlaybackState.STATE_STOPPED
            } else {
                PlaybackState.STATE_PAUSED
            }
        }

        //プレイヤーの情報、現在の再生位置などを設定する
        //また、MediaButtonIntentでできる操作を設定する
        mSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY
                            or PlaybackState.ACTION_PAUSE
                            or PlaybackState.ACTION_SKIP_TO_NEXT
                            or PlaybackState.ACTION_STOP
                            or PlaybackState.ACTION_SEEK_TO
                            or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                            or PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM
                )
//                .setState(state, mediaPlayer.currentPosition.toLong(), mediaPlayer.playbackParams.speed)
                .setState(state, mediaPlayer.currentPosition.toLong(), 1f)
                .setActiveQueueItemId(queueIndex.toLong())
//                .addCustomAction()
                .setExtras(Bundle().also {
                    it.putString(BUNDLE_KEY_PLAYBACK_STATE_REPEAT, repeatMode.toString())
                    it.putBoolean(BUNDLE_KEY_PLAYBACK_STATE_SHUFFLE, shuffleMode)
                })
                .build()
        )
    }

    private fun createNotification() {
        val controller = mSession.controller
        val mediaMetadata = controller.metadata
        if (mediaMetadata == null || !mSession.isActive) {
//            Log.w(TAG, "createNotificationが中断された mSession.isActive: ${mSession.isActive}")
            return
        }
        val description = mediaMetadata.description
        val builder = Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(description.title)
            .setContentText(description.subtitle)
            .setSubText(description.description)
            .setLargeIcon(bitmapCurrentAlbumArt)
            .setContentIntent(  // 通知をクリックしたときのインテントを設定
                PendingIntent.getActivity(
                    applicationContext,
                    INTENT_ID,
                    Intent(applicationContext, MainActivity::class.java)
                        .putExtra(MainActivity.INTENT_BOOL_EXTRA_START_PLAYER_FRAGMENT, true),
//                        .setFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY),
//                        .setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            ) // 通知がスワイプして消された際のインテントを設定
            .setDeleteIntent(buildMediaButtonPendingIntent(this, PlaybackState.ACTION_STOP))
            // 通知の範囲をpublicにしてロック画面に表示されるようにする
            .setVisibility(Notification.VISIBILITY_PUBLIC)
//            .setSmallIcon(R.drawable.exo_controls_play) //通知の領域に使う色を設定
            .setSmallIcon(iconNotification)
            //Androidのバージョンによってスタイルが変わり、色が適用されない場合も多い
//            .setColor(ContextCompat.getColor(this, R.color.colorAccent)) // Media Styleを利用する
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mSession.sessionToken) //通知を小さくたたんだ時に表示されるコントロールのインデックスを設定
                    .setShowActionsInCompactView(1)
            )

        //通知のコントロールの設定
        builder.addAction(
            Notification.Action.Builder(
                iconPrevious, "prev",
                buildMediaButtonPendingIntent(
                    this,
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
            ).build()

        )
        //プレイヤーの状態で再生、一時停止のボタンを設定
        if (controller.playbackState.run { this != null && state != PlaybackState.STATE_PLAYING }) {
            builder.addAction(
                Notification.Action.Builder(
                    iconPlay, "play",
                    buildMediaButtonPendingIntent(this, PlaybackState.ACTION_PLAY)
                ).build()
            )
        } else {
            builder.addAction(
                Notification.Action.Builder(
                    iconPause, "pause",
                    buildMediaButtonPendingIntent(this, PlaybackState.ACTION_PAUSE)
                ).build()
            )
        }
        builder.addAction(
            Notification.Action.Builder(
                iconNext, "next",
                buildMediaButtonPendingIntent(this, PlaybackState.ACTION_SKIP_TO_NEXT)
            ).build()
        )
        when (repeatMode) {
            RepeatMode.NoRepeat -> builder.addAction(
                Notification.Action.Builder(
                    iconRepeat, "repeat mode",
                    buildRepeatModePendingIntent(this, RepeatMode.RepeatQueue)
                ).build()
            )

            RepeatMode.RepeatQueue -> builder.addAction(
                Notification.Action.Builder(
                    iconRepeatOn, "repeat mode",
                    buildRepeatModePendingIntent(this, RepeatMode.RepeatOneMusic)
                ).build()
            )

            RepeatMode.RepeatOneMusic -> builder.addAction(
                Notification.Action.Builder(
                    iconRepeatOneOn, "repeat mode",
                    buildRepeatModePendingIntent(this, RepeatMode.NoRepeat)
                ).build()
            )
        }

        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "aaa", NotificationManager.IMPORTANCE_DEFAULT).apply {
                this.description = "説明文"
                setSound(null, null)
                lightColor = R.color.white
                enableLights(false)
                enableVibration(false)
            }
        ) //現在の曲の情報を設定
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            startForeground(NOTIFICATION_ID, builder.build())
        } else {
            try {
                startForeground(NOTIFICATION_ID, builder.build())
            } catch (e: ForegroundServiceStartNotAllowedException) {
                startActivity(
                    Intent(applicationContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                Log.d(TAG, "startActivityをよんだぞ", e)
            }
        }
        //再生中以外ではスワイプで通知を消せるようにする
        if (controller.playbackState.run { this != null && state != PlaybackState.STATE_PLAYING }) {
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    private fun updateAudioFxFromSharedPrefs() {
        bassBoost.apply {
            setStrength(Prefs.getBassBoost(applicationContext) ?: 0.toShort())
            enabled = true
        }
        equalizer.apply {
            val medium = ((bandLevelRange[0] + bandLevelRange[1]) / 2).toShort()
            val savedEq = Prefs.getEqualizer(applicationContext)
            val eqParams = savedEq?.bandLevels?.toMutableList() ?: ArrayList()
            for (i in eqParams.size until numberOfBands) {
                eqParams += medium
            }
            for (i in 0 until numberOfBands) {
                setBandLevel(i.toShort(), eqParams[i])
            }
            savedEq?.let { enabled = it.enabled }
        }

    }

}


private fun buildMediaButtonPendingIntent(ctx: Context, mediaAction: Long): PendingIntent {
    val keyCode = when (mediaAction) {
        PlaybackState.ACTION_PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
        PlaybackState.ACTION_PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
        PlaybackState.ACTION_SKIP_TO_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
        PlaybackState.ACTION_SKIP_TO_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        PlaybackState.ACTION_STOP -> KeyEvent.KEYCODE_MEDIA_STOP
        PlaybackState.ACTION_FAST_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        PlaybackState.ACTION_REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
        PlaybackState.ACTION_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        else -> throw IllegalArgumentException("mediaActionの値: $mediaAction は想定外だぞ")
    }
    return buildPendingIntentBase(
        ctx,
        keyCode,
        MusicService.INTENT_ACTION_MEDIA_BUTTON,
        Intent.EXTRA_KEY_EVENT,
        KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
    )
}

private fun buildRepeatModePendingIntent(ctx: Context, repeatMode: MusicService.RepeatMode): PendingIntent =
    buildPendingIntentBase(
        ctx, 0, MusicService.INTENT_ACTION_CHANGE_REPEAT_MODE, MusicService.INTENT_EXTRA_REPEAT_MODE, repeatMode
    )

private fun buildPendingIntentBase(ctx: Context, req: Int, action: String, exKey: String, exVal: Any): PendingIntent {
    val intent = Intent(ctx, MusicService::class.java).apply {
        setAction(action)
        when (exVal) {
            is KeyEvent -> putExtra(exKey, exVal)
            is MusicService.RepeatMode -> putExtra(exKey, exVal)
            else -> throw IllegalArgumentException("extraKeyの型: ${exVal::class.qualifiedName} は想定外だぞ")
        }
    }
    return PendingIntent.getService(
        ctx, req, intent,
        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_IMMUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
