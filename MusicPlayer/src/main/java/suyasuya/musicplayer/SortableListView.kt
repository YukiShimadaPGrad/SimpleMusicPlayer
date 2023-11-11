package suyasuya.musicplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.widget.AbsListView.RecyclerListener
import android.widget.AdapterView.OnItemLongClickListener
import kotlin.math.abs


open class SortableListView : ListView, OnItemLongClickListener, RecyclerListener {
    private val scroller = Scroller()
    private val scrollHandler = Handler(Looper.getMainLooper())
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var imageView: ImageView? = null
    private var bitmap: Bitmap? = null
    private var topInWindow = 0
    private var actionDownEvent: MotionEvent? = null
    private var isDragging = false
    private var draggingPosition = -1

    private lateinit var adapter: ArrayAdapter<Any>
    private var previousDataCount = 0

    constructor(context: Context?) : super(context) {
        initialize()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize()
    }

    private fun initialize() {
        onItemLongClickListener = this
        setRecyclerListener(this)
        density = resources.displayMetrics.density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // ListViewのWindowに対する位置を記録する。
        val posArray = IntArray(2)
        getLocationInWindow(posArray)
        topInWindow = posArray[1]
    }

    override fun layoutChildren() {
        // 移動中のビューをinvisibleにする。
        super.layoutChildren()
        if (isDragging) {
            getChildAtPosition(draggingPosition).visibility = INVISIBLE
        }
    }

    override fun onMovedToScrapHeap(view: View) {
        // 子ビューがフレームアウトし、ScrapHeapに回収されたときに呼ばれる。
        // 子ビューは移動中で、invisibleになっているかもしれない。visibleに戻してあげる。
        view.visibility = VISIBLE
    }

    override fun handleDataChanged() {
        // データが1つ追加されたとき、表示位置をリスト終端に移動する。
        super.handleDataChanged()
//        if (adapter == null) {
//            adapter = getAdapter() as ArrayAdapter<*>
//            previousDataCount = adapter!!.count
//            return
//        }
        if (adapter.count == previousDataCount + 1) {
            smoothScrollToPosition(adapter.count - 1)
        }
        previousDataCount = adapter.count
    }

    override fun onItemLongClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long): Boolean {
        // ロングクリック時、ドラッグを開始する。
//        if (adapter == null) {
//            adapter = getAdapter() as ArrayAdapter<*>
//            previousDataCount = adapter!!.count
//        }
        return startDrag(i, actionDownEvent)
    }

//    override fun onTouchEvent(ev: MotionEvent): Boolean {
//        when (ev.action) {
//            MotionEvent.ACTION_DOWN -> storeMotionEvent(ev)
//            MotionEvent.ACTION_MOVE -> if (duringDrag(ev)) {
//                return true
//            }
//
//            MotionEvent.ACTION_UP -> return if (stopDrag(ev, true)) true else performClick()
//            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> if (stopDrag(ev, false)) {
//                return true
//            }
//        }
//        return super.onTouchEvent(ev)
//    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> storeMotionEvent(ev)
            MotionEvent.ACTION_MOVE -> if (duringDrag(ev)) {
                return true
            }

            MotionEvent.ACTION_UP -> if (stopDrag(ev, true)) {
                return true
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> if (stopDrag(ev, false)) {
                return true
            }
        }
        return super.onTouchEvent(ev)
    }


    @Deprecated("ArrayAdapterしか受け取りたくないんだ", ReplaceWith("setArrayAdapter(adapter)"))
    override fun setAdapter(adapter: ListAdapter) {
        @Suppress("Unchecked_cast")
        try {
            this.adapter = adapter as ArrayAdapter<Any>
        } catch (e: ClassCastException) {
            throw IllegalArgumentException("ArrayAdapter<Any> しか受け入れません!")
        }
        super.setAdapter(adapter)
    }


    fun setArrayAdapter(adapter: ArrayAdapter<*>) {
        @Suppress("DEPRECATION")
        setAdapter(adapter)
    }

    private fun startDrag(position: Int, ev: MotionEvent?): Boolean {
        draggingPosition = position
        val view = getChildAtPosition(position)
        val canvas = Canvas()
        val wm = windowManager

        // 移動元のviewからbitmapを生成
        bitmap = Bitmap.createBitmap(view.width, view.height, BITMAP_CONFIG)
        canvas.setBitmap(bitmap)
        view.draw(canvas)

        // 移動中のアイテムを示すimageViewを生成
        imageView = ImageView(context)
        imageView!!.setBackgroundColor(BACKGROUND_COLOR)
        imageView!!.setImageBitmap(bitmap)
        initLayoutParams(view, ev)
        wm.addView(imageView, layoutParams)

        // 移動元のviewを非表示にする
        view.visibility = INVISIBLE

        // スクロール開始
        scrollHandler.postDelayed(scroller, 50)

        // ドラッグ開始
        isDragging = true
        return true
    }

    private fun duringDrag(ev: MotionEvent): Boolean {
        if (!isDragging || imageView == null) {
            return false
        }
        val y = ev.y
        val height = height

        // スクロール速度の決定
        // ドラッグ開始から500 ms未満の時はスクロールしない。
        // 両端のスクロールエリア内のときは、エリア内の位置に応じてスピードを決める。
        val speed = if (ev.eventTime - ev.downTime < 500) {
            0f
        } else if (y < SCROLL_AREA * density) {
            (y / SCROLL_AREA / density - 1) * (SCROLL_SPEED_MAX - SCROLL_SPEED_MIN) - SCROLL_SPEED_MIN
        } else if (y > height - SCROLL_AREA * density) {
            SCROLL_SPEED_MIN + (1 - (height - y) / SCROLL_AREA / density) * (SCROLL_SPEED_MAX - SCROLL_SPEED_MIN)
        } else {
            0f
        }
        scroller.setSpeed(speed * density)

        // ImageViewの位置を更新
        updateLayoutParams(ev)
        windowManager.updateViewLayout(imageView, layoutParams)

        // アイテムの入れ替え
        val currentPosition = pointToPosition(ev.x.toInt(), ev.y.toInt())
        if (currentPosition != draggingPosition && currentPosition != INVALID_POSITION) {
            val item = adapter.getItem(draggingPosition)

            adapter.setNotifyOnChange(false)
            adapter.remove(item)
            adapter.setNotifyOnChange(true)
            adapter.insert(item, currentPosition)
            draggingPosition = currentPosition
        }
        return true
    }

    private fun stopDrag(ev: MotionEvent, isDrop: Boolean): Boolean {
        if (!isDragging) {
            return false
        }
        if (isDrop) {
            duringDrag(ev)
        }
        isDragging = false

        // ImageViewの消去
        windowManager.removeView(imageView)
        imageView = null
        bitmap = null
        actionDownEvent!!.recycle()
        actionDownEvent = null

        // 非表示にしたitemを再表示
        val view = getChildAtPosition(draggingPosition)
        view.visibility = VISIBLE

        // スクロール停止
        scrollHandler.removeCallbacksAndMessages(null)
        return true
    }

    private val windowManager: WindowManager
        get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun initLayoutParams(view: View, ev: MotionEvent?) {
        layoutParams = WindowManager.LayoutParams()
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        layoutParams.format = PixelFormat.TRANSLUCENT
        layoutParams.windowAnimations = 0
        val posArray = IntArray(2)
        view.getLocationInWindow(posArray)
        layoutParams.x = posArray[0]
        layoutParams.y = topInWindow + ev!!.y.toInt() - view.height / 2
    }

    private fun updateLayoutParams(ev: MotionEvent) {
        layoutParams.y = topInWindow + ev.y.toInt() - imageView!!.height / 2
    }

    private fun getChildAtPosition(position: Int): View {
        return getChildAt(position - firstVisiblePosition)
    }

    private fun storeMotionEvent(ev: MotionEvent) {
        actionDownEvent = MotionEvent.obtain(ev)
    }

    private inner class Scroller : Runnable {
        val minimumDuration = 50
        val maximumDuration = 200
        var distance = 0
        var duration = 50
        fun setSpeed(speed: Float) {
            // speedの単位はpx/sec
            // speed / 1000 * dur = 1 <-> dur = 1000/speed
            if (speed == 0f || 1000 / abs(speed) > maximumDuration) {
                distance = 0
                duration = maximumDuration
            } else {
                duration = abs(1000 / speed).toInt()
                if (duration < minimumDuration) {
                    duration = minimumDuration
                    distance = (speed / 1000 * duration).toInt()
                } else {
                    distance = 1
                }
            }
        }

        override fun run() {
            smoothScrollBy(distance, duration)
            scrollHandler.postDelayed(this, duration.toLong())
        }
    }


    companion object {
        // 配列順序を入れ替えられるListView
        // アイテム長押しでドラッグ開始。掴んだアイテムは非表示化され、アイテムのイメージコピーが生成される。イメージコピーは移動可能。
        // アイテム掴んだまま移動させることで、前後のアイテムと順序入れ替える。
        // アイテム離すとその場にドロップ。
        // アイテム掴んだ状態で、ビューの上部/下部に持っていくと、リストがスクロールする。スクロールスピードは可変。
        private val BITMAP_CONFIG = Bitmap.Config.ARGB_8888
        private val BACKGROUND_COLOR = Color.argb(128, 0xFF, 0xFF, 0xFF)
        private const val SCROLL_SPEED_MAX = 750 //at dp/sec   …最大スクロールスピード
        private const val SCROLL_SPEED_MIN = 0 //at dp/sec     …最小スクロールスピード
        private const val SCROLL_AREA = 120 //at dp           …スクロール範囲
        private var density = 0f
    }
}