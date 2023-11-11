@file:Suppress("DEPRECATION")

package suyasuya.musicplayer

import android.app.Fragment
import android.database.DataSetObserver
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.session.MediaController
import android.media.session.MediaSession.Token
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Switch
import suyasuya.musicplayer.Prefs.Companion.EqualizerParams

class AudioFxFragment : Fragment() {
    companion object {
        //        private val TAG = this::class.qualifiedNameWithoutCompanion!!
        private const val ARG_PARAM1 = "param1"
        private const val ARG_PARAM2 = "param2"

        @JvmStatic
        fun newInstance(audioSessionId: Int, mediaSessionToken: Token) =
            AudioFxFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PARAM1, audioSessionId)
                    putParcelable(ARG_PARAM2, mediaSessionToken)
                }
            }
    }

    private lateinit var equalizer: Equalizer
    private lateinit var bassBoost: BassBoost
    private lateinit var eaAdapter: EqualizerArrayAdapter
    private lateinit var mController: MediaController

    private val adapterObserver = object : DataSetObserver() {
        override fun onChanged() {
            for (i in 0 until eaAdapter.count) {
                equalizer.setBandLevel(i.toShort(), eaAdapter.getItem(i)!!.bandLevel)
            }
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            bundle.getInt(ARG_PARAM1).also {
                equalizer = Equalizer(1, it)
                bassBoost = BassBoost(1, it)
            }
            mController = MediaController(context, bundle.getParcelableCMP(ARG_PARAM2, Token::class)!!)
        } ?: throw IllegalStateException("newInstance() を経由しないでインスタンス化された")
        val eqParams = (0 until equalizer.numberOfBands).map { equalizer.getBandLevel(it.toShort()) }
        eaAdapter =
            EqualizerArrayAdapter(context.applicationContext, equalizer.bandLevelRange, eqParams.mapIndexed { i, v ->
                EqualizerArrayAdapter.EqualizerParam(equalizer.getCenterFreq(i.toShort()), v)
            })
        eaAdapter.registerDataSetObserver(adapterObserver)

        setHasOptionsMenu(true)
        activity.invalidateOptionsMenu()
//        bassBoost.enabled = true
    }

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_equalizer, container, false).apply {
            findViewById<Switch>(R.id.switch_use_equlizer).also {
                it.isChecked = equalizer.enabled
                it.setOnCheckedChangeListener { _, isChecked -> equalizer.enabled = isChecked }
            }
            findViewById<ListView>(R.id.list_equalizer).adapter = eaAdapter
            findViewById<SeekBar>(R.id.bass_boost).also {
                it.max = if (bassBoost.strengthSupported) 1000 else 1
                it.progress = clamp(bassBoost.roundedStrength.toInt(), 0, if (bassBoost.strengthSupported) 1000 else 1)
                it.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        bassBoost.setStrength(if (bassBoost.strengthSupported) progress.toShort() else (progress * 1000).toShort())
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
        }


    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onPrepareOptionsMenu(menu: Menu) {
        for (i in 0 until menu.size()) {
            menu.getItem(i).isVisible = false
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith(""))
    override fun onPause() {
        super.onPause()
        Prefs.saveEqualizer(
            context,
            EqualizerParams(
                equalizer.enabled,
                (0 until equalizer.numberOfBands).map { equalizer.getBandLevel(it.toShort()) })
        )
        Prefs.saveBassBoost(context, bassBoost.roundedStrength)
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        bassBoost.release()
        equalizer.release()  // 先にこいつらの解放が必要
        mController.transportControls.sendCustomAction(MusicService.CUSTOM_ACTION_NOTIFY_AUDIO_FX_CHANGED, null)
        eaAdapter.unregisterDataSetObserver(adapterObserver)
        activity.actionBar?.show()
        super.onDestroy()
    }
}