package suyasuya.musicplayer

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.browse.MediaBrowser.MediaItem
import android.net.Uri
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.roundToInt


class MusicArrayAdapter(
    appCtx: Context,
    mediaItems: List<MediaItem>,
    buttonAction: ButtonAction = ButtonAction.None
) : ArrayAdapter<MediaItem>(appCtx, 0, 0, mediaItems) {

    /** ボタンの挙動とアイコンを決める為の共用体もどき */
    sealed class ButtonAction {
        /** 非表示で何もしない */
        object None : ButtonAction()

        /** キューに追加 */
        class AddToQueue(val callback: (MediaItem) -> Unit) : ButtonAction()

        /** キューから削除 */
        object Remove : ButtonAction()

        fun runAction(position: Int, imgButton: ImageButton, self: MusicArrayAdapter) {
            when (this@ButtonAction) {
                is None -> imgButton.visibility = View.GONE
                is AddToQueue -> {
                    imgButton.visibility = View.VISIBLE
                    imgButton.setImageResource(R.drawable.add)
                    imgButton.setOnClickListener { callback(self.getItem(position)!!) }
                }

                is Remove -> {
                    imgButton.visibility = View.VISIBLE
                    imgButton.setImageResource(R.drawable.delete)
                    imgButton.setOnClickListener { self.remove(self.getItem(position)) }
                }
            }
        }
    }


    private val mInflater: LayoutInflater
    private val thumbnailCache = HashMap<Uri, Bitmap>()
    private val defaultThumbnail: Bitmap
    private val thumbnailSize: Size
    private val buttonAction: ButtonAction
    var playingIndex: Int = -1
//        set(value) {
//            parentViewGroup?.getChildAt(field)?.findViewById<ImageView>(R.id.playing)?.visibility = View.GONE
//            field = value
//            parentViewGroup?.getChildAt(field)?.findViewById<ImageView>(R.id.playing)?.visibility = View.VISIBLE
//        }

    init {
        mInflater = LayoutInflater.from(appCtx)
        thumbnailSize = Size(
            appCtx.resources.getDimension(R.dimen.icon_imageview_width).roundToInt(),
            appCtx.resources.getDimension(R.dimen.icon_imageview_height).roundToInt()
        )
        defaultThumbnail = createMatchScaledBitmap(
            createMatchScaledBitmapFromDrawable(appCtx.getDrawable(R.drawable.album_art_default)!!, thumbnailSize),
            thumbnailSize, true
        )
        this.buttonAction = buttonAction
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (holder, convView) = if (convertView == null) {
            mInflater.inflate(R.layout.music_listview_item, parent, false).run {
                val holder = ViewHolder(
                    findViewById(R.id.playing),
                    findViewById(R.id.album_art),
                    findViewById(R.id.title),
                    findViewById(R.id.subtitle),
                    findViewById(R.id.duration),
                    findViewById(R.id.btn_optional)
                )
                tag = holder
                Pair(holder, this)
            }
        } else {
            Pair(convertView.tag as ViewHolder, convertView)
        }
        getItem(position)!!.description.also {
            holder.image.setImageBitmap(
                it.iconBitmap ?: getThumbnailFromCache(context.applicationContext.contentResolver, it.iconUri)
//                it.iconUri?.getBitmapOrNull(context.contentResolver) ?: getThumbnailFromCache(
//                    context.contentResolver,
//                    it.iconUri
//                )

            )
            holder.title.text = it.title
            holder.subTitle.text = it.subtitle
            holder.duration.text = it.extras?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.toTimeString() ?: ""
            buttonAction.runAction(position, holder.optionalButton, this)
        }
        holder.playingIcon.visibility = if (position == playingIndex) View.VISIBLE else View.GONE
        if (buttonAction is ButtonAction.Remove) {
            holder.optionalButton.visibility = if (position == playingIndex) View.GONE else View.VISIBLE
        }
        return convView
    }

    fun getItems(): ArrayList<MediaItem> {
        return ArrayList((0 until count).map { getItem(it)!! })
    }

    private data class ViewHolder(
        val playingIcon: ImageView,
        val image: ImageView,
        val title: TextView,
        val subTitle: TextView,
        val duration: TextView,
        val optionalButton: ImageButton
    )

    private fun getThumbnailFromCache(contentResolver: ContentResolver, key: Uri?): Bitmap {
        return if (key == null) defaultThumbnail
        else if (!thumbnailCache.contains(key)) {
            var thumb = if (key.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE) {
                @SuppressLint("DiscouragedApi")
                val id = context.resources.getIdentifier(key.pathSegments[1], key.pathSegments[0], key.host)
                context.getDrawable(id)?.let { createMatchScaledBitmapFromDrawable(it, thumbnailSize) }
                    ?: defaultThumbnail
            } else {
                key.getAlbumArtBitmapOrElse(contentResolver, thumbnailSize) { defaultThumbnail }
            }
            if (thumb !== defaultThumbnail) {
                thumb = createMatchScaledBitmap(thumb, thumbnailSize, true)
            }
            thumbnailCache[key] = thumb
            thumb
        } else {
            thumbnailCache[key]!!
        }
    }
}