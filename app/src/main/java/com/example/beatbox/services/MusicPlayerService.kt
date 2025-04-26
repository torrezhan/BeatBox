package com.example.beatbox.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.os.Build
import android.content.Context
import android.net.Uri // Import Uri
import androidx.core.app.NotificationCompat
import com.example.beatbox.R
import com.example.beatbox.models.Track
import android.util.Log // Import Log for error handling
import androidx.core.net.toUri

class MusicPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val CHANNEL_ID = "BeatBoxChannel"
    private var currentTrackIndex: Int = -1 // Initialize to -1 (invalid index)
    // Change to MutableList or keep as ArrayList if preferred
    private var trackList: ArrayList<Track> = ArrayList() // Store the full list

    companion object {
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREV = "ACTION_PREV"
        const val EXTRA_TRACKS = "EXTRA_TRACKS"
        const val EXTRA_TRACK_INDEX = "EXTRA_TRACK_INDEX" // New extra for index
        private const val NOTIFICATION_ID = 1 // Define a constant for notification ID
        private const val TAG = "MusicPlayerService" // Tag for logging
         const val ACTION_SET_TRACK = "ACTION_SET_TRACK" // New Action
    }

    override fun onCreate() {
        super.onCreate()
        // Optional: Initialize things only once if needed
        createNotificationChannel() // Create channel on service creation
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")

        // Retrieve track list and index when starting playback
        if (intent?.action == ACTION_PLAY) {
            // Get the list of tracks, handle potential null or different type
            val receivedTracks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(EXTRA_TRACKS, Track::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(EXTRA_TRACKS)
            }

            if (receivedTracks != null) {
                 // Only update the list if a new one is provided
                 if (trackList != receivedTracks) { // Avoid unnecessary list updates
                     trackList = receivedTracks
                     Log.d(TAG, "Received new track list with size: ${trackList.size}")
                 }
            } else if (trackList.isEmpty()) {
                 // If no tracks received and list is empty, we can't play
                 Log.w(TAG, "Received PLAY action but track list is null or empty.")
                 stopSelf() // Stop the service if there's nothing to play
                 return START_NOT_STICKY
            }


            // Get the starting index, default to -1 if not provided
            val startIndex = intent.getIntExtra(EXTRA_TRACK_INDEX, -1)
            Log.d(TAG, "Received start index: $startIndex")

            if (startIndex != -1) {
                 // Only play if the index is valid
                 if (startIndex >= 0 && startIndex < trackList.size) {
                     currentTrackIndex = startIndex
                     playCurrentTrack()
                 } else {
                     Log.e(TAG, "Received invalid start index: $startIndex for list size: ${trackList.size}")
                     // Optionally play the first track if index is invalid but list exists?
                     // Or just stop? Let's stop for now.
                     stopSelf()
                     return START_NOT_STICKY
                 }
            } else if (mediaPlayer != null && !mediaPlayer!!.isPlaying && currentTrackIndex != -1) {
                // Resume current track if index is -1 (e.g., from notification resume)
                 Log.d(TAG, "Resuming track index: $currentTrackIndex")
                 mediaPlayer?.start()
                 updateNotification() // Update notification to show playing state
            } else {
                 Log.w(TAG, "PLAY action received without a valid index or resumable player state.")
                 // Maybe start playing the first track if list is available?
                 if (trackList.isNotEmpty()) {
                     currentTrackIndex = 0
                     playCurrentTrack()
                 } else {
                     stopSelf()
                     return START_NOT_STICKY
                 }
            }

        } else { // Handle other actions
            when (intent?.action) {
                ACTION_PAUSE -> pausePlayback()
                ACTION_NEXT -> nextTrack()
                ACTION_PREV -> prevTrack()
            }
        }

        return START_STICKY // Keep service running until explicitly stopped
    }

    private fun playCurrentTrack() {
        if (currentTrackIndex < 0 || currentTrackIndex >= trackList.size) {
            Log.e(TAG, "playCurrentTrack: Invalid index $currentTrackIndex")
            // Consider stopping service or handling error
             stopSelf()
            return
        }

        val trackToPlay = trackList[currentTrackIndex]
        Log.d(TAG, "Attempting to play track: ${trackToPlay.name} at index $currentTrackIndex, URI: ${trackToPlay.uri}")

        try {
            // Release previous player instance if it exists
            mediaPlayer?.stop() // Stop first before release
            mediaPlayer?.release()
            mediaPlayer = null

            val trackUri = trackToPlay.uri.toUri() // Parse the content URI string

            mediaPlayer = MediaPlayer().apply {
                // Use setDataSource with Context and Uri for content URIs
                setDataSource(applicationContext, trackUri)
                setOnPreparedListener { mp ->
                    Log.d(TAG, "MediaPlayer prepared, starting playback.")
                    mp.start()
                    updateNotification() // Update notification once prepared and playing
                }
                setOnCompletionListener {
                    Log.d(TAG, "Track completed, playing next.")
                    // Optionally play next track automatically
                    nextTrack()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error - what: $what, extra: $extra for URI: ${trackToPlay.uri}")
                    // Handle error, maybe stop service or skip track
                    // Reset player state
                    mp.reset()
                    // Consider stopping the service or trying the next track
                    stopSelf() // Stop service on error for now
                    true // Error handled
                }
                Log.d(TAG, "Preparing MediaPlayer asynchronously.")
                prepareAsync() // Use prepareAsync for network/content URIs
            }

            // Show notification immediately, indicating loading/playing
            updateNotification() // Show initial notification

        } catch (e: Exception) {
            Log.e(TAG, "Error setting data source or preparing MediaPlayer for URI: ${trackToPlay.uri}", e)
            // Handle exceptions like SecurityException, IOException
            mediaPlayer?.release()
            mediaPlayer = null
            stopSelf() // Stop service on critical error
        }
    }


    private fun pausePlayback() {
        if (mediaPlayer?.isPlaying == true) {
            Log.d(TAG, "Pausing playback")
            mediaPlayer?.pause()
            updateNotification() // Update notification to show paused state
            // Make service stoppable when paused?
            // stopForeground(false) // Allow notification to be swiped away when paused? Or keep it ongoing? Depends on desired behavior.
        } else {
             Log.d(TAG, "Pause command received but player not playing.")
        }
    }

    private fun nextTrack() {
        if (trackList.isEmpty()) {
            Log.w(TAG,"Next track requested but track list is empty.")
            return
        }
        currentTrackIndex = (currentTrackIndex + 1) % trackList.size // Loop back to start
        Log.d(TAG, "Playing next track, index: $currentTrackIndex")
        playCurrentTrack()
    }

    private fun prevTrack() {
         if (trackList.isEmpty()) {
             Log.w(TAG,"Previous track requested but track list is empty.")
             return
         }
        // Simple previous logic: go back one, wrap around to end if at the beginning
        currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else trackList.size - 1
        Log.d(TAG, "Playing previous track, index: $currentTrackIndex")
        playCurrentTrack()
    }

    // Renamed from createNotification to updateNotification for clarity
    private fun updateNotification() {
        if (currentTrackIndex < 0 || currentTrackIndex >= trackList.size) {
            // Don't show notification if state is invalid
            Log.w(TAG, "updateNotification called with invalid index: $currentTrackIndex")
            return
        }

        val currentTrack = trackList[currentTrackIndex]
        val isPlaying = mediaPlayer?.isPlaying ?: false
        val contentText = "${currentTrack.name} - ${currentTrack.artist}"

        Log.d(TAG, "Updating notification: isPlaying=$isPlaying, content='$contentText'")

        // Create intents for notification actions
        val playPauseIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            // No need to include track list/index for play/pause of current track
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicPlayerService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 1, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Use different request code
        )

        val prevIntent = Intent(this, MusicPlayerService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(
            this, 2, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Use different request code
        )

        // Build the notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeatBox")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(isPlaying) // Keep notification active while playing
             // Add actions - ensure icons exist
             .addAction(R.drawable.ic_previous, "Previous", prevPendingIntent)
             .addAction(
                 if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                 if (isPlaying) "Pause" else "Play", playPausePendingIntent
             )
             .addAction(R.drawable.ic_next, "Next", nextPendingIntent)
            // Optional: Add style for media notifications
             .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                 // .setMediaSession(mediaSession?.sessionToken) // If using MediaSession
                 .setShowActionsInCompactView(0, 1, 2) // Show Prev, Play/Pause, Next in compact view
             )
            .build()

        // Display the notification as foreground
        startForeground(NOTIFICATION_ID, notification)
    }

    // Create Notification Channel (call once, e.g., in onCreate)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW // Use LOW to avoid sound/vibration
            ).apply {
                description = "Channel for BeatBox music player controls"
                // Configure other channel properties if needed (e.g., setSound(null, null))
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created.")
        }
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy called. Releasing MediaPlayer.")
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}