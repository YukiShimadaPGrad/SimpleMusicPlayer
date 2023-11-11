@file:Suppress("DEPRECATION")

package suyasuya.musicplayer

import android.app.Fragment
import android.database.DataSetObserver
import android.media.MediaMetadata
import android.media.browse.MediaBrowser.MediaItem
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup


class QueueFragment : Fragment() {

    companion object {
        private val TAG = this::class.qualifiedNameWithoutCompanion!!
        private const val ARG_PARAM1 = "param1"
//        private const val ARG_PARAM2 = "param2"

        @JvmStatic
        fun newInstance(mediaSessionToken: MediaSession.Token) =
            QueueFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, mediaSessionToken)
                }
            }
    }

    private lateinit var mController: MediaController
    private lateinit var maAdapter: MusicArrayAdapter


    private val controllerCallback: MediaController.Callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mController.playbackState?.activeQueueItemId?.let {
                if (it == MediaSession.QueueItem.UNKNOWN_ID.toLong()) {
                    Log.w(TAG, "onMetadataChanged mController.playbackState.activeQueueItemIdがUnknownだ")
                } else {
                    maAdapter.playingIndex = it.toInt()
                    maAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mController.playbackState?.activeQueueItemId?.let {
                if (it == MediaSession.QueueItem.UNKNOWN_ID.toLong()) {
                    Log.w(TAG, "onMetadataChanged mController.playbackState.activeQueueItemIdがUNKNOWN_IDだ")
                } else if (it != maAdapter.playingIndex.toLong()) {
                    Log.w(TAG, "queueItemId: $it")
                    maAdapter.playingIndex = it.toInt()
                    maAdapter.notifyDataSetChanged()
                } else {
                }
            }
        }
    }

    private val adapterObserver = object : DataSetObserver() {
        override fun onChanged() {
            mController.transportControls.sendCustomAction(MusicService.CUSTOM_ACTION_RENEW_QUEUE, Bundle().apply {
                putParcelableArrayList(MusicService.BUNDLE_KEY_QUEUE, maAdapter.getItems())
            })
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mController = MediaController(context, it.getParcelableCMP(ARG_PARAM1, MediaSession.Token::class)!!)
        } ?: throw IllegalStateException("newInstance() を経由しないでインスタンス化された")
        //サービスから送られてくるプレイヤーの状態や曲の情報が変更された際のコールバックを設定
        mController.registerCallback(controllerCallback)
        maAdapter = MusicArrayAdapter(
            context.applicationContext,
            mController.queue?.map { MediaItem(it.description, MediaItem.FLAG_PLAYABLE) } ?: ArrayList(),
            MusicArrayAdapter.ButtonAction.Remove
        )
        maAdapter.registerDataSetObserver(adapterObserver)
        controllerCallback.onMetadataChanged(mController.metadata)

        setHasOptionsMenu(true)
        activity.invalidateOptionsMenu()
    }

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_queue, container, false).apply {
            findViewById<SortableListView>(R.id.queue_listview).also {
                it.setArrayAdapter(maAdapter)
                it.divider = null
                it.setOnItemClickListener { _, _, position, _ ->
                    Log.d(TAG, "AdapterView.OnItemClickListener")
                    mController.transportControls.skipToQueueItem(position.toLong())
                }
            }
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onPrepareOptionsMenu(menu: Menu) {
        for (i in 0 until menu.size()) {
            if (menu.getItem(i).itemId !in intArrayOf(
                    R.id.action_save_playlist,
                    R.id.action_player,
                    R.id.action_equalizer
                )
            ) {
                menu.getItem(i).isVisible = false
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        maAdapter.unregisterDataSetObserver(adapterObserver)
        mController.unregisterCallback(controllerCallback)
        super.onDestroy()
    }

}