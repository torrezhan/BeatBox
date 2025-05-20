package com.example.beatbox.activities

import android.Manifest
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.beatbox.models.Track
import com.example.beatbox.services.MusicPlayerService
import com.example.beatbox.utils.FileUtils
import com.example.beatbox.R
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.activity.result.IntentSenderRequest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val tracksList: ArrayList<Track> = ArrayList()
    private var pendingDeleteTrack: Track? = null

    private val CHANNEL_ID = "BeatBoxChannel"
    private val NOTIFICATION_ID = 2

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("MainActivity", "Notification permission granted")
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
            checkMediaPermission()
        }

    private val requestMediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("MainActivity", "Media permission granted")
                loadMusicFromStorage()
            } else {
                Toast.makeText(this, "Media permission denied. App cannot function without this permission.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val requestDeletePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            Log.d("MainActivity", "Permission result received: ${result.resultCode}")
            when (result.resultCode) {
                RESULT_OK -> {
                    pendingDeleteTrack?.let { track ->
                        try {
                            Log.d("MainActivity", "Attempting to delete track after permission granted: ${track.name}")
                            if (deleteTrack(track)) {
                                val position = tracksList.indexOf(track)
                                if (position != -1) {
                                    tracksList.removeAt(position)
                                    (listView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
                                    Toast.makeText(this, "Track deleted successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Log.e("MainActivity", "Track not found in list after permission granted: ${track.name}")
                                    Toast.makeText(this, "Failed to delete track", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error deleting track after permission granted: ${track.name}", e)
                            Toast.makeText(this, "Failed to delete track", Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Log.e("MainActivity", "No pending track to delete after permission granted")
                        Toast.makeText(this, "Failed to delete track", Toast.LENGTH_SHORT).show()
                    }
                }
                RESULT_CANCELED -> {
                    Log.d("MainActivity", "User cancelled the permission request")
                    Toast.makeText(this, "Permission request cancelled", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Log.e("MainActivity", "Unexpected result code: ${result.resultCode}")
                    Toast.makeText(this, "Failed to delete track", Toast.LENGTH_SHORT).show()
                }
            }
            pendingDeleteTrack = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.listView)
        setSupportActionBar(findViewById(R.id.toolbar))
        checkPermissions()
        showLastPlayedTrackNotification()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshMusicList()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshMusicList() {
        if (hasMediaPermission()) {
            loadMusicFromStorage()
            Toast.makeText(this, "List refreshed", Toast.LENGTH_SHORT).show()
        } else {
            requestMediaPermission()
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkMediaPermission()
            }
        } else {
            checkMediaPermission()
        }
    }

    private fun checkMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestMediaPermission()
        } else {
            loadMusicFromStorage()
        }
    }

    private fun requestMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (shouldShowRequestPermissionRationale(permission)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("This app needs access to your music files to play them. Please grant the permission.")
                .setPositiveButton("OK") { _, _ ->
                    requestMediaPermissionLauncher.launch(permission)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Toast.makeText(this, "Permission denied. App cannot function without this permission.", Toast.LENGTH_LONG).show()
                    finish()
                }
                .show()
        } else {
            requestMediaPermissionLauncher.launch(permission)
        }
    }

    private fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadMusicFromStorage() {
        val musicFiles = FileUtils.getAllAudioFiles(this)
        tracksList.clear()
        tracksList.addAll(musicFiles)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tracksList.map { it.name })
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val track = tracksList[position]
            Log.d("MusicPlayer", "Track clicked: ${track.name}, Position: $position")
            
            startMusicService(track)
            
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("track", track)
                putExtra("position", position)
                putExtra("tracks", tracksList)
            }
            startActivity(intent)
        }

        listView.setOnItemLongClickListener { _, view, position, _ ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.track_menu, popup.menu)
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete Track")
                            .setMessage("Are you sure you want to delete this track?")
                            .setPositiveButton("Yes") { _, _ ->
                                val track = tracksList[position]
                                handleDeleteTrack(track, position)
                            }
                            .setNegativeButton("No", null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
            true
        }

        Toast.makeText(this, "Found ${tracksList.size} tracks", Toast.LENGTH_SHORT).show()
    }

    private fun handleDeleteTrack(track: Track, position: Int) {
        try {
            if (deleteTrack(track)) {
                tracksList.removeAt(position)
                (listView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
                Toast.makeText(this, "Track deleted successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: RecoverableSecurityException) {
            Log.d("MainActivity", "Requesting delete permission for track: ${track.name}")
            pendingDeleteTrack = track
            try {
                val intentSender = e.userAction.actionIntent.intentSender
                val intentSenderRequest = IntentSenderRequest.Builder(intentSender)
                    .setFillInIntent(null)
                    .build()
                requestDeletePermissionLauncher.launch(intentSenderRequest)
            } catch (ex: Exception) {
                Log.e("MainActivity", "Error launching permission request", ex)
                Toast.makeText(this, "Failed to request delete permission", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error deleting track", e)
            Toast.makeText(this, "Failed to delete track", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteTrack(track: Track): Boolean {
        return try {
            val deleted = contentResolver.delete(track.uri, null, null) > 0
            if (!deleted) {
                Log.e("MainActivity", "Failed to delete track: ${track.name}")
                Toast.makeText(this, "Failed to delete track", Toast.LENGTH_SHORT).show()
            }
            deleted
        } catch (e: RecoverableSecurityException) {
            Log.e("MainActivity", "RecoverableSecurityException while deleting track: ${track.name}", e)
            throw e
        } catch (e: Exception) {
            Log.e("MainActivity", "Error deleting track: ${track.name}", e)
            Toast.makeText(this, "Failed to delete track", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun startMusicService(track: Track) {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            putParcelableArrayListExtra(MusicPlayerService.EXTRA_TRACKS, tracksList)
            putExtra(MusicPlayerService.EXTRA_TRACK_INDEX, tracksList.indexOf(track))
            action = MusicPlayerService.ACTION_PLAY
        }
        startService(intent)
    }

    private fun showLastPlayedTrackNotification() {
        val prefs = getSharedPreferences("MusicPlayerPrefs", Context.MODE_PRIVATE)
        val lastTrackName = prefs.getString("last_track_name", null)
        val lastTrackArtist = prefs.getString("last_track_artist", null)

        if (lastTrackName != null && lastTrackArtist != null) {
            createNotificationChannel()

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Last Played Track")
                .setContentText("$lastTrackName - $lastTrackArtist")
                .setSmallIcon(R.drawable.ic_music_note)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Last Played Track",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows the last played track when app starts"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Do you want to exit the app?")
            .setPositiveButton("Yes") { _, _ ->
                // Stop the music service before exiting
                val serviceIntent = Intent(this, MusicPlayerService::class.java)
                serviceIntent.action = MusicPlayerService.ACTION_STOP
                startService(serviceIntent)
                // Give the service a moment to stop
                Thread.sleep(100)
                super.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the music service when the app is closed
        val serviceIntent = Intent(this, MusicPlayerService::class.java)
        serviceIntent.action = MusicPlayerService.ACTION_STOP
        startService(serviceIntent)
    }
}
