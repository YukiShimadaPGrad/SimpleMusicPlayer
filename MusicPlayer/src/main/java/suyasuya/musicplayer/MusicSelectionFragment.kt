@file:Suppress("DEPRECATION")

package suyasuya.musicplayer

import android.app.Fragment
import android.app.FragmentTransaction
import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.Toast

class MusicSelectionFragment : Fragment() {

    companion object {
        private val TAG = this::class.qualifiedNameWithoutCompanion!!
        private const val ARG_PARAM1 = "param1"
        private const val ARG_PARAM2 = "param2"

        @JvmStatic
        fun newInstance(mediaId: String, allowAddToQueue: Boolean) =
            MusicSelectionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, mediaId)
                    putBoolean(ARG_PARAM2, allowAddToQueue)
                }
            }
    }


    private lateinit var maAdapter: MusicArrayAdapter
    private lateinit var mBrowser: MediaBrowser
    private lateinit var mController: MediaController
    private lateinit var mediaId: String


    //接続時に呼び出されるコールバック
    private val connectionCallback: MediaBrowser.ConnectionCallback =
        object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                try {
                    mController = MediaController(context, mBrowser.sessionToken)

                } catch (ex: RemoteException) {
                    ex.printStackTrace()
                    Toast.makeText(context, ex.message, Toast.LENGTH_LONG).show()
                }
                mBrowser.subscribe(mediaId, subscriptionCallback)
            }
        }

    //Subscribeした際に呼び出されるコールバック
    private val subscriptionCallback: MediaBrowser.SubscriptionCallback =
        object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: List<MediaBrowser.MediaItem>) {
                Log.d(TAG, "onChildrenLoaded parentId: $parentId, children: $children")
                maAdapter.setNotifyOnChange(false)
                maAdapter.clear()
                maAdapter.addAll(children)
                maAdapter.setNotifyOnChange(true)
                maAdapter.notifyDataSetChanged()
            }
        }

    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mediaId = it.getString(ARG_PARAM1)!!
            maAdapter = MusicArrayAdapter(activity.applicationContext, ArrayList(),
                if (it.getBoolean(ARG_PARAM2)) {
                    MusicArrayAdapter.ButtonAction.AddToQueue {
                        mController.transportControls.sendCustomAction(MusicService.CUSTOM_ACTION_ADD_QUEUE,
                            Bundle().apply { putParcelable(MusicService.BUNDLE_KEY_MEDIA_ITEM, it) })
                    }
                } else MusicArrayAdapter.ButtonAction.None)
        } ?: throw IllegalStateException("newInstance() を経由しないでインスタンス化された")

        mBrowser = MediaBrowser(context, ComponentName(context, MusicService::class.java), connectionCallback, null)
        mBrowser.connect()

        setHasOptionsMenu(true)
        activity.invalidateOptionsMenu()

    }


    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_music_selection, container, false).also { it ->
            activity.invalidateOptionsMenu()
            it.findViewById<ListView>(R.id.music_listview).also {
                it.adapter = maAdapter
                it.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    Log.d(TAG, "AdapterView.OnItemClickListener")
                    val mediaItem = maAdapter.getItem(position)!!
                    if (mediaItem.isBrowsable) {
                        fragmentManager.beginTransaction()
                            .addToBackStack(null)
                            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                            .replace(R.id.fragment_container, newInstance(mediaItem.mediaId!!, true))
                            .commit()
                    } else {
                        mController.transportControls.playFromMediaId(mediaItem.mediaId, null)
                        fragmentManager.beginTransaction()
                            .addToBackStack(null)
                            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                            .replace(R.id.fragment_container, PlayerFragment.newInstance(mBrowser.sessionToken))
                            .commit()
                    }
                }
                it.divider = null
            }
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onPrepareOptionsMenu(menu: Menu) {
        for (i in 0 until menu.size()) {
            if (menu.getItem(i).itemId !in intArrayOf(
                    R.id.action_queue,
                    R.id.action_player,
                    R.id.action_reload,
                    R.id.action_equalizer
                )
            ) {
                menu.getItem(i).isVisible = false
            }
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        mBrowser.disconnect()
        super.onDestroy()
    }
}