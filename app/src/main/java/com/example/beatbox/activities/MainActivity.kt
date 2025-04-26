package com.example.beatbox.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.beatbox.models.Track
import com.example.beatbox.services.MusicPlayerService
import com.example.beatbox.utils.FileUtils
import com.example.beatbox.R
import android.util.Log
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private val tracksList: ArrayList<Track> = ArrayList()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                loadMusicFromStorage()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.listView)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        checkPermissionAndLoad()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionAndLoad() {
        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            loadMusicFromStorage()
        }
    }

    private fun loadMusicFromStorage() {
        val musicFiles = FileUtils.getAllAudioFiles(this)
        tracksList.clear()

        musicFiles.forEach { file ->
            tracksList.add(Track(file.name, "Unknown", file.uri))
        }

        // Создаем ArrayAdapter для ListView и устанавливаем его
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tracksList.map { it.name })
        listView.adapter = adapter

        // Обработка нажатия на элемент списка
        listView.setOnItemClickListener { _, _, position, _ ->
            val track = tracksList[position]
            Log.d("MusicPlayer", "Track clicked: ${track.name}, Position: $position")
            startMusicService(track)  // Запуск сервиса для воспроизведения трека
        }

        Toast.makeText(this, "Found ${tracksList.size} tracks", Toast.LENGTH_SHORT).show()
    }

    // Запуск сервиса для воспроизведения трека
    private fun startMusicService(track: Track) {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            putParcelableArrayListExtra(MusicPlayerService.EXTRA_TRACKS, tracksList)
            putExtra("TRACK_URI", track.uri)
            action = MusicPlayerService.ACTION_PLAY
        }
        startService(intent)  // Запуск сервиса
    }
}
