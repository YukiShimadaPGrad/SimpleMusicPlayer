@file:Suppress("DEPRECATION")

package suyasuya.musicplayer

import android.app.Fragment
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.*

class PlaylistEditorFragment : Fragment() {

    companion object {
        //        private val TAG = this::class.qualifiedNameWithoutCompanion!!
        private const val ARG_PARAM1 = "param1"
//        private const val ARG_PARAM2 = "param2"

        @JvmStatic
        fun newInstance(sessionToken: MediaSession.Token) =
            PlaylistEditorFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM1, sessionToken)
                }
            }
    }

    private lateinit var titleAdapter: ArrayAdapter<String>
    private lateinit var mediaController: MediaController


    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mediaController = MediaController(context, it.getParcelableCMP(ARG_PARAM1, MediaSession.Token::class)!!)
            titleAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1)
            loadTitles()
        } ?: throw IllegalStateException("newInstance() を経由しないでインスタンス化された")
        setHasOptionsMenu(true)
        activity.invalidateOptionsMenu()
    }


    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_playlist_editor, container, false).also { view ->
        val ctx = context.applicationContext
        val etSave = view.findViewById<EditText>(R.id.playlist_title_save)
        val etDel = view.findViewById<EditText>(R.id.playlist_title_delete)
        view.findViewById<ImageButton>(R.id.save_playlist).setOnClickListener {
            val title = etSave.text.toString()
            if (title.isEmpty()) etSave.error = ""
            else {
                mediaController.queue?.let { queue ->
                    Prefs.addPlaylist(ctx, title, queue.mapNotNull { it.description.mediaUri })
                    showMsg("Saved/Updated \"$title\".")
                    fragmentManager.popBackStack()
                } ?: showMsg("Queue is null.")
            }
        }
        view.findViewById<ImageButton>(R.id.delete_playlist).setOnClickListener {
            val title = etDel.text.toString()
            if (title.isEmpty()) etDel.error = ""
            else {
                Prefs.deletePlaylist(ctx, title)
                showMsg("Deleted \"$title\".")
            }
            loadTitles()
        }
        view.findViewById<ListView>(R.id.playlists).apply {
            adapter = titleAdapter
            setOnItemClickListener { _, _, position, _ -> etSave.setText(titleAdapter.getItem(position)) }
            setOnItemLongClickListener { _, _, position, _ -> etDel.setText(titleAdapter.getItem(position)); true }
        }
    }


    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onPrepareOptionsMenu(menu: Menu) {
        for (i in 0 until menu.size()) {
            menu.getItem(i).isVisible = false
        }
    }


    private fun loadTitles() {
        titleAdapter.setNotifyOnChange(false)
        titleAdapter.clear()
        titleAdapter.setNotifyOnChange(true)
        titleAdapter.addAll(Prefs.getPlaylistTitles(context.applicationContext))
    }

    private fun showMsg(msg: CharSequence) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

}