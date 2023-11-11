package suyasuya.musicplayer

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView


class EqualizerArrayAdapter(
    appCtx: Context,
    bandLevelRange: ShortArray,
    equalizerParams: List<EqualizerParam>,
) : ArrayAdapter<EqualizerArrayAdapter.EqualizerParam>(appCtx, 0, 0, equalizerParams) {

    /** イコライザのバンド中心周波数とバンドレベルの組 */
    data class EqualizerParam(var milliHz: Int, var bandLevel: Short)

    private val mInflater: LayoutInflater
    private val bandLevelMin: Short
    private val bandLevelMax: Short


    init {
        mInflater = LayoutInflater.from(appCtx)
        if (bandLevelRange.size != 2) {
            throw IllegalArgumentException("bandLevelRange の要素数が2じゃなかった 要素: ${bandLevelRange.size}")
        }
        bandLevelMin = bandLevelRange[0]
        bandLevelMax = bandLevelRange[1]
        if (bandLevelMax < bandLevelMin) {
            throw IllegalArgumentException("bandLevelRange の要素が min, max の順番じゃなかった 要素: ${bandLevelRange.size}")
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (holder, convView) = if (convertView == null) {
            mInflater.inflate(R.layout.equalizer_listview_item, parent, false).run {
                val holder = ViewHolder(findViewById(R.id.frequency), findViewById(R.id.band_level))
                tag = holder
                Pair(holder, this)
            }
        } else {
            Pair(convertView.tag as ViewHolder, convertView)
        }
        getItem(position)!!.also {
            @SuppressLint("SetTextI18n")
            holder.txtFreq.text = "${it.milliHz / 1000} Hz"
            holder.seekLevel.min = bandLevelMin.toInt()
            holder.seekLevel.max = bandLevelMax.toInt()
            holder.seekLevel.progress = it.bandLevel.toInt()
            holder.seekLevel.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                var prg: Int = 0
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                    it.bandLevel = progress.toShort()
//                    this@EqualizerArrayAdapter.notifyDataSetChanged() //ここでやるとカクカクする
                    prg = progress
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    it.bandLevel = prg.toShort()
                    this@EqualizerArrayAdapter.notifyDataSetChanged()
                }
            })
        }
        return convView
    }

    private data class ViewHolder(val txtFreq: TextView, val seekLevel: SeekBar)
}