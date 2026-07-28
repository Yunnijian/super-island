package io.github.superisland.testsource

import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

/** Internal test-only public MediaSession used to validate the standard-mode media source. */
class TestMediaSessionService : Service() {
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "SuperIslandMediaTest").apply { isActive = true }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TestSourceActions.START_MEDIA -> publish(track = 1, state = PlaybackState.STATE_PLAYING)
            TestSourceActions.UPDATE_MEDIA -> publish(track = 2, state = PlaybackState.STATE_PLAYING)
            TestSourceActions.PAUSE_MEDIA -> publish(track = 2, state = PlaybackState.STATE_PAUSED)
            TestSourceActions.STOP_MEDIA -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session.release()
        Log.i(TAG, "test media session stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publish(track: Int, state: Int) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "媒体测试曲目 #$track")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "超级岛测试歌手")
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(state, 0L, 1f, SystemClock.elapsedRealtime())
                .build(),
        )
        Log.i(TAG, "test media session state=$state track=$track")
    }

    private companion object {
        const val TAG = "SuperIslandTest"
    }
}
