package suyasuya.musicplayer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Fragment
import android.app.FragmentTransaction
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import suyasuya.musicplayer.MusicService.Companion.CUSTOM_ACTION_RELOAD_MUSIC_LIBRARY
import java.util.*


class MainActivity : Activity() {
    companion object {
        val TAG = this::class.qualifiedNameWithoutCompanion!!
        const val INTENT_BOOL_EXTRA_START_PLAYER_FRAGMENT = "intent_extra_start_player_fragment"
        const val REQUEST_CODE_PERMISSION = 1
    }

    private lateinit var permissionForReadAudio: String
    private lateinit var mBrowser: MediaBrowser
    private lateinit var mController: MediaController

    //接続時に呼び出されるコールバック
    private val connectionCallback: MediaBrowser.ConnectionCallback =
        object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                try {
                    //接続が完了するとSessionTokenが取得できるので
                    //それを利用してMediaControllerを作成
                    mController = MediaController(this@MainActivity, mBrowser.sessionToken)
                } catch (ex: RemoteException) {
                    ex.printStackTrace()
                    Toast.makeText(this@MainActivity, ex.message, Toast.LENGTH_LONG).show()
                    return
                }

                if (intent.getBooleanExtra(INTENT_BOOL_EXTRA_START_PLAYER_FRAGMENT, false)
                    && !mController.queue.isNullOrEmpty()
                ) {
                    changeFragment(PlayerFragment.newInstance(mBrowser.sessionToken), false)
                } else {
                    changeFragment(MusicSelectionFragment.newInstance(mBrowser.root, false), false)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate savedInstanceState: $savedInstanceState")
        setContentView(R.layout.activity_main)
        lockScreenOrientation(this, true)

        permissionForReadAudio = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            Manifest.permission.READ_MEDIA_AUDIO
        }
        if (checkSelfPermission(permissionForReadAudio) == PackageManager.PERMISSION_GRANTED) {
            startMusicService()
        } else {
//            if shouldShowRequestPermissionRationale()
            requestPermissions(arrayOf(permissionForReadAudio), REQUEST_CODE_PERMISSION)
        }


    }

    override fun onDestroy() {
        if (checkSelfPermission(permissionForReadAudio) == PackageManager.PERMISSION_GRANTED) {
            mBrowser.disconnect()
        }
        lockScreenOrientation(this, false)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean =
        if (checkSelfPermission(permissionForReadAudio) == PackageManager.PERMISSION_GRANTED) {
            menuInflater.inflate(R.menu.menu_main, menu)
            if (!mBrowser.isConnected || mController.metadata == null) {
                menu!!.findItem(R.id.action_player).isVisible = false
            }
            true
        } else {
            false
        }


    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_queue -> {
            changeFragment(QueueFragment.newInstance(mBrowser.sessionToken), true)
            true
        }

        R.id.action_player -> {
            changeFragment(PlayerFragment.newInstance(mBrowser.sessionToken), true)
            true
        }

        R.id.action_save_playlist -> {
            changeFragment(PlaylistEditorFragment.newInstance(mBrowser.sessionToken), true)
            true
        }

        R.id.action_reload -> {
            if (mBrowser.isConnected) {
                mController.transportControls.sendCustomAction(CUSTOM_ACTION_RELOAD_MUSIC_LIBRARY, null)
                startActivity(
                    Intent(this, this::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } else false
        }

        R.id.action_equalizer -> {
            mController.sendCommand(
                MusicService.COMMAND_GET_AUDIO_SESSION_ID,
                null,
                object : ResultReceiver(Handler(mainLooper)) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        changeFragment(AudioFxFragment.newInstance(resultCode, mBrowser.sessionToken), true)
                    }
                })
            true
        }

        else -> super.onOptionsItemSelected(item)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionsResult")
        when (requestCode) {
            REQUEST_CODE_PERMISSION -> {
                for (i in permissions.indices) {
                    when (permissions[i]) {
                        permissionForReadAudio -> {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                startMusicService()
                                invalidateOptionsMenu()
                            } else {
                                finish()
                            }
                        }

                    }
                }
            }

            else -> Log.w(TAG, "\t想定外のrequestCode: $requestCode だったぞ")
        }
    }

    private fun startMusicService() {
        startService(Intent(this, MusicService::class.java))
        mBrowser = MediaBrowser(this, ComponentName(this, MusicService::class.java), connectionCallback, null)
        mBrowser.connect()
    }

    @Suppress("DEPRECATION")
    private fun changeFragment(
        fragment: Fragment,
        useBackStack: Boolean,
        transition: Int = FragmentTransaction.TRANSIT_FRAGMENT_FADE
    ) {
        fragmentManager.beginTransaction().apply {
            if (useBackStack) {
                addToBackStack(null)
            }
            setReorderingAllowed(true)
            setTransition(transition)
            replace(R.id.fragment_container, fragment)
            commit()
        }
    }
}

/**
 * 画面の回転を固定・解除する関数
 * @param flg 真なら回転固定 偽なら回転可能
 */
@SuppressLint("SourceLockedOrientationActivity")
private fun lockScreenOrientation(activity: Activity, flg: Boolean) {
    if (flg) {
        @Suppress("DEPRECATION")
        when ((activity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.rotation) {
            Surface.ROTATION_90 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Surface.ROTATION_180 -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            Surface.ROTATION_270 -> activity.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

            else -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
