package com.example.beatbox.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.beatbox.R
import com.example.beatbox.models.Track
import android.util.Log

class MusicPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var CHANNEL_ID = "BeatBoxChannel"
    private var currentTrackIndex: Int = -1
    private var trackList: ArrayList<Track> = ArrayList()
    private var isPlaying: Boolean = false
    private var currentTrack: Track? = null

    companion object {
        const val ACTION_PLAY = "com.example.beatbox.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.beatbox.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.beatbox.ACTION_NEXT"
        const val ACTION_PREV = "com.example.beatbox.ACTION_PREV"
        const val ACTION_STOP = "com.example.beatbox.ACTION_STOP"
        const val ACTION_TRACK_CHANGED = "com.example.beatbox.ACTION_TRACK_CHANGED"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.example.beatbox.ACTION_PLAYBACK_STATE_CHANGED"
        const val EXTRA_TRACK = "com.example.beatbox.EXTRA_TRACK"
        const val EXTRA_IS_PLAYING = "com.example.beatbox.EXTRA_IS_PLAYING"
        const val EXTRA_TRACKS = "EXTRA_TRACKS"
        const val EXTRA_TRACK_INDEX = "EXTRA_TRACK_INDEX"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "MusicPlayerService"
        private const val PREFS_NAME = "MusicPlayerPrefs"
        private const val KEY_LAST_TRACK_INDEX = "last_track_index"
        private const val KEY_LAST_TRACK_NAME = "last_track_name"
        private const val KEY_LAST_TRACK_ARTIST = "last_track_artist"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")

        when (intent?.action) {
            ACTION_PLAY -> {
                Log.d(TAG, "Handling PLAY action. Current state: isPlaying=$isPlaying, mediaPlayer=${mediaPlayer != null}")
                
                val receivedTracks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_TRACKS, Track::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(EXTRA_TRACKS)
                }

                if (receivedTracks != null) {
                    trackList = receivedTracks
                }

                val startIndex = intent.getIntExtra(EXTRA_TRACK_INDEX, -1)
                Log.d(TAG, "Received start index: $startIndex, current index: $currentTrackIndex")

                if (startIndex != -1 && startIndex >= 0 && startIndex < trackList.size) {
                    // If a new track is selected, stop current playback and start the new track
                    if (mediaPlayer != null) {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }
                    currentTrackIndex = startIndex
                    playCurrentTrack()
                } else if (mediaPlayer == null) {
                    Log.e(TAG, "Cannot create media player without valid index")
                    stopSelf()
                    return START_NOT_STICKY
                } else if (!isPlaying) {
                    mediaPlayer?.start()
                    isPlaying = true
                    updateNotification()
                    sendPlaybackStateBroadcast()
                }
            }
            ACTION_PAUSE -> {
                Log.d(TAG, "Handling PAUSE action. Current state: isPlaying=$isPlaying, mediaPlayer=${mediaPlayer != null}")
                if (mediaPlayer != null && isPlaying) {
                    mediaPlayer?.pause()
                    isPlaying = false
                    updateNotification()
                    sendPlaybackStateBroadcast()
                }
            }
            ACTION_NEXT -> {
                playNext()
            }
            ACTION_PREV -> {
                playPrevious()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Handling STOP action")
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                isPlaying = false
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun playPrevious() {
        if (trackList.isEmpty()) {
            Log.w(TAG, "Previous track requested but track list is empty.")
            return
        }

        val currentPosition = mediaPlayer?.currentPosition ?: 0
        if (currentPosition > 3000) {
            mediaPlayer?.seekTo(0)
        } else {
            currentTrackIndex = if (currentTrackIndex > 0) {
                currentTrackIndex - 1
            } else {
                trackList.size - 1
            }
            Log.d(TAG, "Playing previous track, index: $currentTrackIndex")
            playCurrentTrack()
        }
    }

    private fun playNext() {
        if (trackList.isEmpty()) {
            Log.w(TAG, "Next track requested but track list is empty.")
            return
        }
        currentTrackIndex = (currentTrackIndex + 1) % trackList.size
        Log.d(TAG, "Playing next track, index: $currentTrackIndex")
        playCurrentTrack()
    }

    private fun playCurrentTrack() {
        if (currentTrackIndex < 0 || currentTrackIndex >= trackList.size) {
            Log.e(TAG, "playCurrentTrack: Invalid index $currentTrackIndex")
            stopSelf()
            return
        }

        val trackToPlay = trackList[currentTrackIndex]
        Log.d(TAG, "Attempting to play track: ${trackToPlay.name} at index $currentTrackIndex, URI: ${trackToPlay.uri}")

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, trackToPlay.uri)
                setOnPreparedListener { mp ->
                    Log.d(TAG, "MediaPlayer prepared, starting playback.")
                    mp.start()
                    this@MusicPlayerService.isPlaying = true
                    currentTrack = trackToPlay
                    saveLastPlayedTrack(trackToPlay)
                    updateNotification()
                    sendTrackChangedBroadcast()
                    sendPlaybackStateBroadcast()
                }
                setOnCompletionListener {
                    Log.d(TAG, "Track completed, playing next.")
                    playNext()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error - what: $what, extra: $extra for URI: ${trackToPlay.uri}")
                    mp.reset()
                    this@MusicPlayerService.isPlaying = false
                    sendPlaybackStateBroadcast()
                    stopSelf()
                    true
                }
                Log.d(TAG, "Preparing MediaPlayer asynchronously.")
                prepareAsync()
            }

            currentTrack = trackToPlay
            sendTrackChangedBroadcast()

        } catch (e: Exception) {
            Log.e(TAG, "Error setting data source or preparing MediaPlayer for URI: ${trackToPlay.uri}", e)
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            sendPlaybackStateBroadcast()
            stopSelf()
        }
    }

    private fun updateNotification() {
        if (currentTrackIndex < 0 || currentTrackIndex >= trackList.size) {
            Log.w(TAG, "updateNotification called with invalid index: $currentTrackIndex")
            return
        }

        val currentTrack = trackList[currentTrackIndex]
        val contentText = "${currentTrack.name} - ${currentTrack.artist}"

        Log.d(TAG, "Updating notification: isPlaying=$isPlaying, content='$contentText'")

        val playPauseIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicPlayerService::class.java).apply { 
            action = ACTION_NEXT 
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 1, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, MusicPlayerService::class.java).apply { 
            action = ACTION_PREV 
        }
        val prevPendingIntent = PendingIntent.getService(
            this, 2, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeatBox")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .addAction(R.drawable.ic_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play", 
                playPausePendingIntent
            )
            .addAction(R.drawable.ic_next, "Next", nextPendingIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Channel for BeatBox music player"
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created.")
    }

    private fun sendTrackChangedBroadcast() {
        currentTrack?.let { track ->
            val intent = Intent(ACTION_TRACK_CHANGED).apply {
                putExtra(EXTRA_TRACK, track)
            }
            sendBroadcast(intent)
            Log.d(TAG, "Sent track changed broadcast for track: ${track.name}")
        }
    }

    private fun sendPlaybackStateBroadcast() {
        val intent = Intent(ACTION_PLAYBACK_STATE_CHANGED).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Sent playback state broadcast: isPlaying=$isPlaying")
    }

    private fun saveLastPlayedTrack(track: Track) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_LAST_TRACK_INDEX, currentTrackIndex)
            putString(KEY_LAST_TRACK_NAME, track.name)
            putString(KEY_LAST_TRACK_ARTIST, track.artist)
            apply()
        }
        Log.d(TAG, "Saved last played track: ${track.name}")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service is being destroyed")
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        stopForeground(true)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed, stopping service")
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}