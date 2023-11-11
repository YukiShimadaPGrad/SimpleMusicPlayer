@file:Suppress("DEPRECATION")

package suyasuya.musicplayer

import android.app.Fragment
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import suyasuya.musicplayer.MusicService.RepeatMode.*


class PlayerFragment : Fragment() {

    companion object {
        private val TAG = this::class.qualifiedNameWithoutCompanion!!
        private const val ARG_PARAM1 = "param1"
//        private const val ARG_PARAM2 = "param2"

        @JvmStatic
        fun newInstance(mediaSessionToken: MediaSession.Token) =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, mediaSessionToken)
                }
            }
    }

    //    private lateinit var mBrowser: MediaBrowser
    private lateinit var mController: MediaController
    private lateinit var btnPlayOrPause: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnShuffle: ImageButton

    //    private late init var btnRepeat: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var txtTitle: TextView
    private lateinit var txtSubTitle: TextView
    private lateinit var txtPosition: TextView
    private lateinit var txtDuration: TextView
    private lateinit var imgAlbumArt: ImageView


    //MediaControllerのコールバック
    private val controllerCallback: MediaController.Callback = object : MediaController.Callback() {
        //再生中の曲の情報が変更された際に呼び出される
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(TAG, "onMetadataChanged metadata: ${metadata?.description}")
            if (metadata == null) return
            txtTitle.text = metadata.description.title
            txtSubTitle.text = metadata.description.subtitle
            txtDuration.text = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).toTimeString()
            Size(imgAlbumArt.width, imgAlbumArt.height).also {
                imgAlbumArt.setImageBitmap(
                    Uri.parse(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)).getAlbumArtBitmapOrElse(
                        context.contentResolver, it
                    ) { _ ->
                        createMatchScaledBitmapFromDrawable(
                            context.resources.getDrawable(
                                R.drawable.album_art_default,
                                null
                            ), it, true
                        )
                    })
            }
            if (imgAlbumArt.drawable == null) imgAlbumArt.setImageResource(R.drawable.album_art_default)
            seekBar.max = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).toInt()
        }

        //プレイヤーの状態が変更された時に呼び出される
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(TAG, "onPlaybackStateChanged state: $state")
            if (state == null) {
                Log.w(TAG, "onPlaybackStateChanged stateがnullなのは想定外")
                super.onPlaybackStateChanged(null)
                return
            }

            //プレイヤーの状態によってボタンの挙動とアイコンを変更する
            if (state.state == PlaybackState.STATE_PLAYING) {
                btnPlayOrPause.setOnClickListener { mController.transportControls.pause() }
                btnPlayOrPause.setImageResource(R.drawable.controls_pause)
            } else {
                btnPlayOrPause.setOnClickListener { mController.transportControls.play() }
                btnPlayOrPause.setImageResource(R.drawable.controls_play)
            }
            try {
                when (MusicService.RepeatMode.valueOf(state.extras!!.getString(MusicService.BUNDLE_KEY_PLAYBACK_STATE_REPEAT)!!)) {
                    NoRepeat -> btnRepeat.setImageResource(R.drawable.controls_repeat)
                    RepeatQueue -> btnRepeat.setImageResource(R.drawable.controls_repeat_on)
                    RepeatOneMusic -> btnRepeat.setImageResource(R.drawable.controls_repeat_one_on)
                }
                when (state.extras!!.getBoolean(MusicService.BUNDLE_KEY_PLAYBACK_STATE_SHUFFLE)) {
                    true -> btnShuffle.setImageResource(R.drawable.controls_shuffle_on)
                    else -> btnShuffle.setImageResource(R.drawable.controls_shuffle)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            this@PlayerFragment.txtPosition.text = state.position.toTimeString()
            seekBar.progress = state.position.toInt()
        }
    }


    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mController = MediaController(context, it.getParcelableCMP(ARG_PARAM1, MediaSession.Token::class)!!)
        } ?: throw IllegalStateException("newInstance() を経由しないでインスタンス化された")

        mController.registerCallback(controllerCallback)
        activity.actionBar?.hide()
    }


    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val retView = inflater.inflate(R.layout.fragment_player, container, false).also {
            txtTitle = it.findViewById(R.id.title)
            txtSubTitle = it.findViewById(R.id.subtitle)
            txtPosition = it.findViewById(R.id.position)
            txtDuration = it.findViewById(R.id.duration)
            seekBar = it.findViewById(R.id.seekbar)
            btnPlayOrPause = it.findViewById(R.id.play_or_pause)
            btnRepeat = it.findViewById(R.id.repeat)
            btnShuffle = it.findViewById(R.id.shuffle)
            imgAlbumArt = it.findViewById(R.id.album_art)

            it.findViewById<ImageButton>(R.id.skip_prev)
                .setOnClickListener { mController.transportControls.skipToPrevious() }
            it.findViewById<ImageButton>(R.id.skip_next)
                .setOnClickListener { mController.transportControls.skipToNext() }
        }

        btnRepeat.setOnClickListener {
            mController.transportControls.sendCustomAction(
                MusicService.CUSTOM_ACTION_TOGGLE_REPEAT_MODE, null
            )
        }
        btnShuffle.setOnClickListener {
            mController.transportControls.sendCustomAction(
                MusicService.CUSTOM_ACTION_TOGGLE_SHUFFLE_MODE, null
            )
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                this@PlayerFragment.txtPosition.text = progress.toLong().toTimeString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                this@PlayerFragment.mController.transportControls.seekTo(seekBar!!.progress.toLong())
            }
        })

        imgAlbumArt.also {  // view がスケーリングされてからメタデータを表示する
            it.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    controllerCallback.onMetadataChanged(mController.metadata)
                    controllerCallback.onPlaybackStateChanged(mController.playbackState)
                    it.viewTreeObserver.removeOnGlobalLayoutListener(this)  // SAM変換だとこのthisが使えなくなる
                }
            })
        }

        return retView
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        mController.unregisterCallback(controllerCallback)
        activity.actionBar?.show()
        super.onDestroy()
    }

}