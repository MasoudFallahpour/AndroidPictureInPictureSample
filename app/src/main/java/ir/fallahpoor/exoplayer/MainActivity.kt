package ir.fallahpoor.exoplayer

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.util.Rational
import android.view.View

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.RawResourceDataSource
import com.google.android.exoplayer2.util.Util
import com.google.android.material.snackbar.Snackbar

import java.util.ArrayList

import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private var playerPosition: Long = 0
    private var wasVideoPlaying = true
    private var pictureInPictureParamsBuilder: PictureInPictureParams.Builder? = null
    private var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != ACTION_MEDIA_CONTROL) {
                return
            }

            player.playWhenReady =
                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    CONTROL_TYPE_PLAY -> true
                    CONTROL_TYPE_PAUSE -> false
                    else -> false
                }
        }

    }

    private val isPipModeSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val isPipModePermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.O)
        get() {
            val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            return AppOpsManager.MODE_ALLOWED == appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                Process.myUid(),
                packageName
            )
        }

    private val aspectRatio: Rational
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        get() {
            val videoSurfaceView = playerView.videoSurfaceView
            return Rational(videoSurfaceView.width, videoSurfaceView.height)
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()

        logMessage("onCreated")

    }

    private fun setupViews() {
        enterPipModeImageView.setOnClickListener {
            if (isPipModeSupported) {
                if (isPipModePermissionGranted) {
                    enterPictureInPictureMode(getPictureInPictureParamsBuilder().build())
                } else {
                    showPermissionNotGrantedSnackbar()
                }
            } else {
                showPipModeNotSupportedSnackbar()
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun getPictureInPictureParamsBuilder(): PictureInPictureParams.Builder {
        if (pictureInPictureParamsBuilder == null) {
            pictureInPictureParamsBuilder = PictureInPictureParams.Builder()
            pictureInPictureParamsBuilder?.setAspectRatio(aspectRatio)
        }
        return (pictureInPictureParamsBuilder as PictureInPictureParams.Builder)
    }

    private fun showPermissionNotGrantedSnackbar() {
        Snackbar.make(playerView, R.string.pip_mode_permission_not_granted, Snackbar.LENGTH_LONG)
            .setAction(
                R.string.grant_permission
            ) {
                val intent = Intent(
                    "android.settings.PICTURE_IN_PICTURE_SETTINGS",
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .show()
    }

    private fun showPipModeNotSupportedSnackbar() {
        Snackbar.make(playerView, R.string.pip_mode_not_supported, Snackbar.LENGTH_LONG).show()
    }

    override fun onStart() {

        super.onStart()
        logMessage("onStart")

        if (isPipModeSupported) {
            setupPlayer()
        }

    }

    private fun setupPlayer() {
        player = ExoPlayerFactory.newSimpleInstance(this)
        playerView.player = player
        player.prepare(createMediaSource())
        player.seekTo(playerPosition)
        player.playWhenReady = wasVideoPlaying
        if (isPipModeSupported) {
            player.addListener(object : Player.EventListener {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    if (playWhenReady && playbackState == Player.STATE_READY) {
                        updatePipModeActions(
                            R.drawable.ic_pause_24dp, getString(R.string.pause), CONTROL_TYPE_PAUSE,
                            REQUEST_CODE_PAUSE
                        )
                    } else {
                        updatePipModeActions(
                            R.drawable.ic_play_24dp, getString(R.string.play), CONTROL_TYPE_PLAY,
                            REQUEST_CODE_PLAY
                        )
                    }
                }
            })
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    internal fun updatePipModeActions(
        @DrawableRes iconId: Int,
        title: String,
        controlType: Int,
        requestCode: Int
    ) {

        val actions = ArrayList<RemoteAction>()

        val intent = PendingIntent.getBroadcast(
            this@MainActivity,
            requestCode,
            Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, controlType),
            0
        )
        val icon = Icon.createWithResource(this, iconId)
        actions.add(RemoteAction(icon, title, title, intent))

        getPictureInPictureParamsBuilder().setActions(actions)
        setPictureInPictureParams(pictureInPictureParamsBuilder!!.build())

    }

    private fun createMediaSource(): MediaSource {
        val dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "ExoPlayer"))
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(RawResourceDataSource.buildRawResourceUri(R.raw.video))
    }

    override fun onResume() {

        super.onResume()
        logMessage("onResume")

        if (!isPipModeSupported) {
            setupPlayer()
        }

    }

    override fun onPause() {

        super.onPause()
        logMessage("onPause")

        if (!isPipModeSupported || !isPipModePermissionGranted) {
            wasVideoPlaying = player.playWhenReady
            playerPosition = player.currentPosition
            releasePlayer()
        }

    }

    override fun onStop() {

        super.onStop()
        logMessage("onStop")

        if (isPipModeSupported && isPipModePermissionGranted) {
            wasVideoPlaying = player.playWhenReady
            playerPosition = player.currentPosition
            releasePlayer()
        }

    }

    private fun releasePlayer() {
        playerView.player = null
        player.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        logMessage("onDestroy")
    }

    override fun onPictureInPictureModeChanged(isInPipMode: Boolean) {

        super.onPictureInPictureModeChanged(isInPipMode)

        if (isInPipMode) {
            registerReceiver(broadcastReceiver, IntentFilter(ACTION_MEDIA_CONTROL))
            showHideControls(false)
        } else {
            unregisterReceiver(broadcastReceiver)
            showHideControls(true)
        }

    }

    private fun showHideControls(show: Boolean) {
        if (show) {
            enterPipModeImageView.visibility = View.VISIBLE
            supportActionBar?.show()
        } else {
            enterPipModeImageView.visibility = View.GONE
            supportActionBar?.hide()
        }
        playerView.useController = show
    }

    private fun logMessage(message: String) {
        Log.d(TAG, message)
    }

    private companion object {
        private const val TAG = "@@@@@@"
        private const val ACTION_MEDIA_CONTROL = "media_control"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val REQUEST_CODE_PLAY = 1000
        private const val REQUEST_CODE_PAUSE = 1001
        private const val CONTROL_TYPE_PLAY = 1
        private const val CONTROL_TYPE_PAUSE = 2
    }

}
